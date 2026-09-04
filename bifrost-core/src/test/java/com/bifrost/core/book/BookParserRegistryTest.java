package com.bifrost.core.book;

import com.bifrost.core.book.model.BookResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 注册表单测：路由表正确性 + 未知扩展兜底（{@code doc/m2-book/task/01-图书核心.md} T1.5）。
 */
class BookParserRegistryTest {

    @Test
    void routesKnownExtensions() {
        BookParserRegistry registry = new BookParserRegistry(
                List.of(new EpubBookParser(), new PdfBookParser()));
        assertSame(EpubBookParser.class, registry.find("epub").orElseThrow().getClass());
        assertSame(EpubBookParser.class, registry.find("kepub.epub").orElseThrow().getClass());
        assertSame(PdfBookParser.class, registry.find("pdf").orElseThrow().getClass());
        assertTrue(registry.find("mobi").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void unknownExtensionFallsBackToFileName(@TempDir Path tempDir) throws Exception {
        BookParserRegistry registry = new BookParserRegistry(
                List.of(new EpubBookParser(), new PdfBookParser()));
        Path file = tempDir.resolve("mystery.xyz");
        java.nio.file.Files.writeString(file, "junk");
        BookResult r = registry.parse(file);
        assertTrue(r.parseError());
        assertEquals("mystery", r.title());
    }

    @Test
    void emptyRegistryReturnsEmpty() {
        BookParserRegistry registry = new BookParserRegistry(List.of());
        Optional<BookParser> any = registry.find("epub");
        assertFalse(any.isPresent());
    }
}