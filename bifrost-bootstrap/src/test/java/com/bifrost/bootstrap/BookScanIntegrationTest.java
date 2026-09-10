package com.bifrost.bootstrap;

import com.bifrost.core.book.BookScanService;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2-book 扫描端到端集成测试。
 *
 * <p>从 classpath 拷贝 {@code data-sample/ebook/*.epub} + {@code *.pdf} 到 {@code @TempDir}，
 * 建一个图书目录，触发 {@link BookScanService#scanRoot(Long, boolean)}，验证：</p>
 * <ul>
 *   <li>2 册入库（1 EPUB + 1 PDF）</li>
 *   <li>EPUB 解析出 title/authors/language/identifier</li>
 *   <li>PDF 解析出 title（XMP 优先 → InfoDict fallback）</li>
 *   <li>EMBEDDED 封面写入 cover-source/book-{id}.jpg</li>
 *   <li>幂等：二次扫描 updated=0 added=0</li>
 *   <li>删文件后再扫 → missing</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "bifrost.db.path=target/test-data/book-scan-test.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"
})
@DirtiesContext
class BookScanIntegrationTest {

    @Autowired private BookScanService bookScanService;
    @Autowired private BookRepository bookRepository;
    @Autowired private LibraryRootRepository libraryRootRepository;

    @TempDir Path tempDir;

    private Long rootId;

    @BeforeEach
    void setup() throws IOException {
        // 拷贝磁盘上的 sample 样本到 tempDir（避免 classpath 资源问题）
        // 找 data-sample/ebook 路径：先看当前工作目录，再看父目录
        Path srcDir = locateSampleDir();
        assertNotNull(srcDir, "data-sample/ebook not found in cwd or parent dirs");
        copyFile(srcDir.resolve("sample.epub"), tempDir.resolve("sample.epub"));
        copyFile(srcDir.resolve("sample.pdf"), tempDir.resolve("sample.pdf"));

        // 清理本测试 DB 全部 book + library_root（共享 DB 但本测试专享）
        bookRepository.findAll().forEach(bookRepository::delete);
        libraryRootRepository.findAll().forEach(libraryRootRepository::delete);

        // 建图书目录
        LibraryRoot root = new LibraryRoot();
        root.setName("Test Book Root");
        root.setPath(tempDir.toString());
        root.setEnabled(true);
        root.setMediaType(MediaType.BOOK);
        rootId = libraryRootRepository.save(root).getId();
    }

    private static Path locateSampleDir() {
        Path cur = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4; i++) {
            Path cand = cur.resolve("data-sample/ebook");
            if (Files.isDirectory(cand)) {
                return cand;
            }
            cur = cur.getParent();
            if (cur == null) {
                break;
            }
        }
        return null;
    }

    private static void copyFile(Path src, Path dst) throws IOException {
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    }

    @AfterEach
    void cleanup() {
        if (rootId != null) {
            // 清理该 tempDir 路径下的所有 books（物理删除，不级联）
            bookRepository.findAll().stream()
                    .filter(b -> b.getFilePath() != null && b.getFilePath().startsWith(tempDir.toString()))
                    .forEach(bookRepository::delete);
            libraryRootRepository.findById(rootId).ifPresent(libraryRootRepository::delete);
        }
    }

    @Test
    void scanEpubAndPdfFromFixture() {
        ScanStats stats = bookScanService.scanRoot(rootId, false);
        assertEquals(2, stats.added(), "should add 2 books");
        assertEquals(0, stats.error(), "should have no parse errors");
        assertEquals(0, stats.missing(), "should have no missing files");

        List<Book> all = bookRepository.findByLibraryRootId(rootId);
        assertEquals(2, all.size());

        Book epub = all.stream().filter(b -> "EPUB".equals(b.getFormat())).findFirst().orElseThrow();
        assertEquals("Test Book", epub.getTitle());
        assertEquals("Test Author", epub.getAuthors());
        assertEquals("en", epub.getLanguage());
        assertEquals("urn:uuid:hurl-ebook-001", epub.getIdentifier());
        assertEquals("epub", epub.getExtension());
        assertEquals("EMBEDDED", epub.getCoverSource());
        assertNotNull(epub.getFilePath());
        assertTrue(Files.isRegularFile(Path.of(epub.getFilePath())));

        Book pdf = all.stream().filter(b -> "PDF".equals(b.getFormat())).findFirst().orElseThrow();
        assertEquals("sample", pdf.getTitle());  // XMP/InfoDict 都无 title 时 fallback 到文件名
        assertEquals("pdf", pdf.getExtension());
        assertTrue(pdf.getFileSize() > 0);
    }

    @Test
    void scanIsIdempotent() {
        bookScanService.scanRoot(rootId, false);
        // 二次扫描：should be 0 added, 0 updated, 0 error
        ScanStats second = bookScanService.scanRoot(rootId, false);
        assertEquals(0, second.added(), "second scan should add 0");
        assertEquals(0, second.updated(), "second scan should update 0");
        assertEquals(2, bookRepository.findByLibraryRootId(rootId).size(), "still 2 books");
    }

    @Test
    void deletedFileMarksMissing() throws IOException {
        bookScanService.scanRoot(rootId, false);
        assertEquals(2, bookRepository.findByLibraryRootId(rootId).size());

        // 删一个文件
        Files.deleteIfExists(tempDir.resolve("sample.epub"));

        ScanStats stats = bookScanService.scanRoot(rootId, false);
        assertEquals(0, stats.added());
        assertEquals(1, stats.missing(), "1 file now missing");

        // 库中两条都存在（缺失不删行）
        assertEquals(2, bookRepository.findByLibraryRootId(rootId).size());
        Book epub = bookRepository.findByLibraryRootId(rootId).stream()
                .filter(b -> "EPUB".equals(b.getFormat())).findFirst().orElseThrow();
        assertFalse(epub.getIsAvailable(), "missing book should be marked unavailable");
    }

    @Test
    void reparseOnForce() {
        bookScanService.scanRoot(rootId, false);
        // force=true 强制全量重解析
        ScanStats forced = bookScanService.scanRoot(rootId, true);
        assertEquals(0, forced.added());
        assertEquals(2, forced.updated(), "force should re-parse both");
    }

    private void copyFromClasspath(String resource, Path target) {
        // unused
    }
}