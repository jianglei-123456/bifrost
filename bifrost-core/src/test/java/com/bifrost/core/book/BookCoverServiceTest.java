package com.bifrost.core.book;

import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BookCoverService} 单测：内嵌封面落盘、缩略图、删除（{@code doc/m2-book/task/01-图书核心.md} T1.5）。
 */
@ExtendWith(MockitoExtension.class)
class BookCoverServiceTest {

    @Mock
    private BookRepository bookRepository;

    private final BifrostProperties properties = makeProperties();

    @TempDir
    Path tempDir;

    private BookCoverService service;

    @org.junit.jupiter.api.BeforeEach
    void setup() throws IOException {
        // 重写 coverCacheDir 到 tempDir
        BifrostProperties p = makeProperties();
        p.getMedia().setCoverCacheDir(tempDir);
        this.service = new BookCoverService(p, bookRepository);
    }

    private static BifrostProperties makeProperties() {
        BifrostProperties p = new BifrostProperties();
        return p;
    }

    @Test
    void storeEmbeddedCoverWritesFile() {
        Book b = new Book();
        b.setId(42L);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean wrote = service.storeEmbeddedCover(b, new byte[]{1, 2, 3, 4});
        assertTrue(wrote);
        assertEquals(BookCoverService.EMBEDDED, b.getCoverSource());
        Path expected = service.coverSourceDir().resolve("book-42.jpg");
        assertTrue(Files.isRegularFile(expected));
        verify(bookRepository, times(1)).save(b);
    }

    @Test
    void storeEmbeddedCoverSkipsWhenAlreadySet() {
        Book b = new Book();
        b.setId(7L);
        b.setCoverSource(BookCoverService.EMBEDDED);
        assertFalse(service.storeEmbeddedCover(b, new byte[]{1, 2, 3}));
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void storeEmbeddedCoverSkipsNullData() {
        Book b = new Book();
        b.setId(7L);
        assertFalse(service.storeEmbeddedCover(b, null));
        assertFalse(service.storeEmbeddedCover(b, new byte[0]));
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void deleteRemovesFilesAndClearsSource() throws IOException {
        Book b = new Book();
        b.setId(99L);
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        // 先存，再删
        boolean wrote = service.storeEmbeddedCover(b, new byte[]{1, 2, 3, 4, 5});
        assertTrue(wrote);
        assertEquals(BookCoverService.EMBEDDED, b.getCoverSource());

        // 模拟有缓存文件存在（手动写一个）
        Files.createDirectories(service.coverCacheDir());
        Path cache = service.coverCacheDir().resolve("book-99-200.jpg");
        Files.write(cache, new byte[]{9, 9, 9});
        assertTrue(Files.isRegularFile(cache));

        service.delete(b);
        ArgumentCaptor<Book> capt = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository, times(2)).save(capt.capture());
        Book saved = capt.getValue();
        assertNull(saved.getCoverSource());
        assertFalse(Files.isRegularFile(cache));
        assertFalse(Files.isRegularFile(service.coverSourceDir().resolve("book-99.jpg")));
    }

    @Test
    void coverDataReturnsEmptyWhenNoSource() {
        Book b = new Book();
        b.setId(1L);
        b.setCoverSource(null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(b));
        assertTrue(service.coverData(1L, null).isEmpty());
    }

    @Test
    void coverDataReadsOriginalWhenNoSize() throws IOException {
        Book b = new Book();
        b.setId(5L);
        when(bookRepository.findById(5L)).thenReturn(Optional.of(b));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));

        byte[] data = TestBookFactory.tinyJpeg();
        boolean wrote = service.storeEmbeddedCover(b, data);
        assertTrue(wrote);

        // 验证：原图文件存在 + coverSource 写入
        assertEquals(BookCoverService.EMBEDDED, b.getCoverSource());
        assertTrue(Files.isRegularFile(service.coverSourceDir().resolve("book-5.jpg")));

        Optional<byte[]> result = service.coverData(5L, null);
        assertTrue(result.isPresent());
        assertArrayEquals(data, result.get());
    }
}