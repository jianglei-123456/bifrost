package com.bifrost.core.book;

import com.bifrost.core.book.model.BookResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EPUB 解析器单测（{@code doc/m2-book/task/01-图书核心.md} T1.5）。
 * 样本文件程序化生成（{@link TestBookFactory#writeEpub}）。
 */
class EpubBookParserTest {

    private final EpubBookParser parser = new EpubBookParser();

    @Test
    void parsesFullMetadata(@TempDir Path tempDir) throws Exception {
        byte[] cover = TestBookFactory.tinyJpeg();
        Path file = TestBookFactory.writeEpub(tempDir, "book.epub",
                new TestBookFactory.BookSpec(
                        "Pride and Prejudice",
                        List.of("Jane Austen"),
                        "en",
                        "T. Egerton",
                        "1813",
                        "Classic novel",
                        List.of("Fiction", "Romance"),
                        "urn:uuid:11111111-1111-1111-1111-111111111111",
                        null, null, "Public Domain",
                        cover));
        BookResult r = parser.parse(file);
        assertFalse(r.parseError());
        assertEquals("Pride and Prejudice", r.title());
        assertEquals("Jane Austen", r.authors());
        assertEquals("en", r.language());
        assertEquals("T. Egerton", r.publisher());
        assertEquals(1813, r.pubDate());
        assertEquals("Classic novel", r.description());
        assertEquals("Fiction; Romance", r.subject());
        assertTrue(r.identifier().startsWith("urn:uuid:"));
        assertEquals("Public Domain", r.rights());
        assertEquals("EPUB", r.format());
        assertNotNull(r.embeddedCover());
        assertArrayEquals(cover, r.embeddedCover());
    }

    @Test
    void joinsMultipleAuthors(@TempDir Path tempDir) throws Exception {
        Path file = TestBookFactory.writeEpub(tempDir, "two.epub",
                new TestBookFactory.BookSpec("共著", List.of("Alice", "Bob"), "en",
                        null, null, null, List.of(), "urn:uuid:22222222", null, null, null, null));
        BookResult r = parser.parse(file);
        assertEquals("Alice & Bob", r.authors());
    }

    @Test
    void titleFallsBackToFileNameWhenMissing(@TempDir Path tempDir) throws Exception {
        Path file = TestBookFactory.writeEpub(tempDir, "untitled-book.epub",
                new TestBookFactory.BookSpec("", List.of(), "en", null, null, null, List.of(),
                        "urn:uuid:33333333", null, null, null, null));
        BookResult r = parser.parse(file);
        assertEquals("untitled-book", r.title());
    }

    @Test
    void parsesPubDateYear(@TempDir Path tempDir) throws Exception {
        Path file = TestBookFactory.writeEpub(tempDir, "dated.epub",
                new TestBookFactory.BookSpec("T", List.of(), "en", null, "2024-05-15", null, List.of(),
                        "urn:uuid:44444444", null, null, null, null));
        BookResult r = parser.parse(file);
        assertEquals(2024, r.pubDate());
    }

    @Test
    void parsesSeriesFromBelongsToCollection(@TempDir Path tempDir) throws Exception {
        Path file = TestBookFactory.writeEpub(tempDir, "series.epub",
                new TestBookFactory.BookSpec("T", List.of(), "en", null, null, null, List.of(),
                        "urn:uuid:55555555", "Foundation", "2", null, null));
        BookResult r = parser.parse(file);
        assertEquals("Foundation", r.series());
        assertEquals(2.0, r.seriesIndex());
    }

    @Test
    void corruptedFileFallsBack(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("broken.epub");
        java.nio.file.Files.writeString(file, "not an epub", java.nio.charset.StandardCharsets.UTF_8);
        BookResult r = parser.parse(file);
        assertTrue(r.parseError());
        assertEquals("broken", r.title());
    }
}