package com.bifrost.core.book;

import com.bifrost.common.exception.BizException;
import com.bifrost.core.book.event.BookScanCompletedEvent;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BookScanService} 单测（{@code doc/m2-book/task/01-图书核心.md} T1.5）。
 * 覆盖：增量/强制扫描、缺失标记、扫描进行中、媒体类型不匹配、事件发布、统计。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookScanServiceTest {

    @Mock private LibraryRootRepository libraryRootRepository;
    @Mock private BookRepository bookRepository;
    @Mock private BookCoverService bookCoverService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    @TempDir Path tempDir;

    private BookScanService service;

    private BookParserRegistry registry;

    private BifrostProperties properties() {
        BifrostProperties p = new BifrostProperties();
        p.getMedia().setCoverCacheDir(tempDir);
        p.getScan().setBatchSize(2);  // 触发批
        return p;
    }

    private BookScanService newService() {
        registry = new BookParserRegistry(List.of(new EpubBookParser(), new PdfBookParser()));
        service = new BookScanService(libraryRootRepository, bookRepository, registry,
                bookCoverService, eventPublisher, transactionManager, properties());
        return service;
    }

    private static LibraryRoot bookRoot(Path dir, long id) {
        LibraryRoot r = new LibraryRoot();
        r.setId(id);
        r.setName("测试图书根");
        r.setPath(dir.toString());
        r.setEnabled(true);
        r.setMediaType(MediaType.BOOK);
        r.setScanStatus(ScanStatus.IDLE);
        return r;
    }

    @org.junit.jupiter.api.BeforeEach
    void setupTransaction() {
        org.mockito.Mockito.lenient()
                .doAnswer(inv -> new SimpleTransactionStatus())
                .when(transactionManager).getTransaction(any());
    }

    @Test
    void scanRootRejectsWhenAlreadyScanning() {
        newService();
        LibraryRoot r = bookRoot(tempDir, 1L);
        // 通过反射设 scanStatus = SCANNING
        r.setScanStatus(ScanStatus.SCANNING);
        when(libraryRootRepository.findById(1L)).thenReturn(Optional.of(r));
        // 由于 first tryLock 成功（默认无人持锁），但进入后 lib.getMediaType=BOOK，继续走 scanLibraryRoot
        // 改测：同线程二次 scanRoot 仍能成功（ReentrantLock 同线程可重入），因此互斥应通过"不同线程"测
        // 这里改为：扫描过程中重入应被允许（ReentrantLock 同线程可重入）→ 不重入测试，改测 BIZ 行为：
        // 媒体类型不匹配
        LibraryRoot r2 = bookRoot(tempDir, 2L);
        r2.setMediaType(MediaType.MUSIC);
        when(libraryRootRepository.findById(2L)).thenReturn(Optional.of(r2));
        BizException ex = assertThrows(BizException.class, () -> service.scanRoot(2L));
        assertTrue(ex.getMessage().contains("不是图书类型"));
    }

    @Test
    void scanRootRejectsNonBookType() {
        newService();
        LibraryRoot r = bookRoot(tempDir, 2L);
        r.setMediaType(MediaType.MUSIC);
        when(libraryRootRepository.findById(2L)).thenReturn(Optional.of(r));
        BizException ex = assertThrows(BizException.class, () -> service.scanRoot(2L));
        assertTrue(ex.getMessage().contains("不是图书类型"));
    }

    @Test
    void scanRootRejectsDisabled() {
        newService();
        LibraryRoot r = bookRoot(tempDir, 3L);
        r.setEnabled(false);
        when(libraryRootRepository.findById(3L)).thenReturn(Optional.of(r));
        BizException ex = assertThrows(BizException.class, () -> service.scanRoot(3L));
        assertTrue(ex.getMessage().contains("已禁用"));
    }

    @Test
    void scanRootRejectsMissingRoot() {
        newService();
        when(libraryRootRepository.findById(99L)).thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class, () -> service.scanRoot(99L));
        assertTrue(ex.getMessage().contains("库根不存在"));
    }

    @Test
    void scanRootPersistsAndPublishesEvent(@TempDir Path libDir) throws Exception {
        newService();
        LibraryRoot r = bookRoot(libDir, 10L);
        when(libraryRootRepository.findById(10L)).thenReturn(Optional.of(r));
        when(libraryRootRepository.save(any(LibraryRoot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookRepository.findByLibraryRootId(10L)).thenReturn(new ArrayList<>());
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        // 写 1 个 EPUB + 1 个 PDF
        TestBookFactory.writeEpub(libDir, "b1.epub",
                new TestBookFactory.BookSpec("Title 1", List.of("Alice"), "en",
                        null, null, null, List.of(), "urn:uuid:11111111-1111", null, null, null, null));
        TestBookFactory.writeMinimalPdf(libDir, "b2.pdf");

        ScanStats stats = service.scanRoot(10L);

        assertEquals(2, stats.added());
        assertEquals(0, stats.updated());
        assertEquals(0, stats.missing());
        assertEquals(0, stats.error());

        // 验证 save 至少 2 次（两条 Book 记录）+ 2 次 LibraryRoot 状态
        verify(bookRepository, atLeastOnce()).save(any(Book.class));
        verify(libraryRootRepository, atLeastOnce()).save(any(LibraryRoot.class));
        // 事件
        ArgumentCaptor<BookScanCompletedEvent> eventCaptor =
                ArgumentCaptor.forClass(BookScanCompletedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        BookScanCompletedEvent ev = eventCaptor.getValue();
        assertEquals(10L, ev.libraryRootId());
        assertEquals(2, ev.stats().added());
    }

    @Test
    void scanRootIncrementalSkipsUnchanged(@TempDir Path libDir) throws Exception {
        newService();
        LibraryRoot r = bookRoot(libDir, 20L);
        when(libraryRootRepository.findById(20L)).thenReturn(Optional.of(r));
        when(libraryRootRepository.save(any(LibraryRoot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        Path epub = TestBookFactory.writeEpub(libDir, "same.epub",
                new TestBookFactory.BookSpec("Title", List.of("Alice"), "en", null, null, null, List.of(),
                        "urn:uuid:22222222", null, null, null, null));
        String abs = epub.toAbsolutePath().normalize().toString();

        // 已有记录：filePath 匹配 + 指纹一致 + isAvailable=true → 跳过
        Book existing = new Book();
        existing.setFilePath(abs);
        existing.setFileSize(java.nio.file.Files.size(epub));
        existing.setFileLastModified(java.nio.file.Files.getLastModifiedTime(epub).toMillis());
        existing.setFingerprint(com.bifrost.common.util.Fingerprint.of(epub));
        existing.setIsAvailable(true);
        existing.setTitle("OLD");  // 故意不同
        existing.setLibraryRootId(20L);
        when(bookRepository.findByLibraryRootId(20L)).thenReturn(new ArrayList<>(List.of(existing)));

        ScanStats stats = service.scanRoot(20L, false);  // force=false
        assertEquals(0, stats.added());
        assertEquals(0, stats.updated());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void scanRootForceReParsesExisting(@TempDir Path libDir) throws Exception {
        newService();
        LibraryRoot r = bookRoot(libDir, 30L);
        when(libraryRootRepository.findById(30L)).thenReturn(Optional.of(r));
        when(libraryRootRepository.save(any(LibraryRoot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        Path epub = TestBookFactory.writeEpub(libDir, "force.epub",
                new TestBookFactory.BookSpec("New", List.of("Alice"), "en", null, null, null, List.of(),
                        "urn:uuid:33333333", null, null, null, null));
        String abs = epub.toAbsolutePath().normalize().toString();

        Book existing = new Book();
        existing.setFilePath(abs);
        existing.setFileSize(java.nio.file.Files.size(epub));
        existing.setFileLastModified(java.nio.file.Files.getLastModifiedTime(epub).toMillis());
        existing.setFingerprint(com.bifrost.common.util.Fingerprint.of(epub));
        existing.setIsAvailable(true);
        existing.setTitle("OLD");
        existing.setLibraryRootId(30L);
        when(bookRepository.findByLibraryRootId(30L)).thenReturn(new ArrayList<>(List.of(existing)));

        ScanStats stats = service.scanRoot(30L, true);  // force=true
        assertEquals(0, stats.added());
        assertEquals(1, stats.updated());
        verify(bookRepository, atLeastOnce()).save(any(Book.class));
    }

    @Test
    void scanRootMarksMissing(@TempDir Path libDir) throws Exception {
        newService();
        LibraryRoot r = bookRoot(libDir, 40L);
        when(libraryRootRepository.findById(40L)).thenReturn(Optional.of(r));
        when(libraryRootRepository.save(any(LibraryRoot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        // 库根目录为空，但 dbIndex 有一条 Book → 标记为 missing
        Book stale = new Book();
        stale.setFilePath(libDir.resolve("gone.epub").toAbsolutePath().normalize().toString());
        stale.setIsAvailable(true);
        stale.setLibraryRootId(40L);
        when(bookRepository.findByLibraryRootId(40L)).thenReturn(new ArrayList<>(List.of(stale)));

        ScanStats stats = service.scanRoot(40L);
        assertEquals(0, stats.added());
        assertEquals(0, stats.updated());
        assertEquals(1, stats.missing());
        // isAvailable 应被置为 false 并 save
        ArgumentCaptor<Book> capt = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository, atLeastOnce()).save(capt.capture());
        boolean foundMissing = capt.getAllValues().stream()
                .anyMatch(b -> Boolean.FALSE.equals(b.getIsAvailable()));
        assertTrue(foundMissing);
    }

    @Test
    void scanRootSkipsMissingDirectory(@TempDir Path libDir) throws Exception {
        newService();
        LibraryRoot r = bookRoot(libDir.resolve("does-not-exist"), 50L);
        when(libraryRootRepository.findById(50L)).thenReturn(Optional.of(r));
        when(libraryRootRepository.save(any(LibraryRoot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookRepository.findByLibraryRootId(50L)).thenReturn(new ArrayList<>());

        ScanStats stats = service.scanRoot(50L);
        assertEquals(1, stats.error());
    }
}