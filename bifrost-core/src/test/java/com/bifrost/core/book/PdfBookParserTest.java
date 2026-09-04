package com.bifrost.core.book;

import com.bifrost.core.book.model.BookResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDF 解析器单测（{@code doc/m2-book/task/01-图书核心.md} T1.5）。
 * 样本 PDF 程序化生成（{@link TestBookFactory#writeMinimalPdf}），再用 PDFBox 写入 InfoDict。
 */
class PdfBookParserTest {

    private final PdfBookParser parser = new PdfBookParser();

    @Test
    void parsesInfoDict(@TempDir Path tempDir) throws Exception {
        Path file = TestBookFactory.writeMinimalPdf(tempDir, "info.pdf");
        // PDFBox 写 InfoDict
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("Domain-Driven Design");
            info.setAuthor("Eric Evans");
            info.setSubject("Software development");
            info.setKeywords("DDD, design, software");
            info.setCreator("Acme Editor");
            info.setProducer("Bifrost Test");
            Calendar cal = Calendar.getInstance();
            cal.set(2003, Calendar.AUGUST, 30);
            info.setCreationDate(cal);
            doc.save(file.toFile());
        }
        BookResult r = parser.parse(file);
        assertFalse(r.parseError());
        assertEquals("Domain-Driven Design", r.title());
        assertEquals("Eric Evans", r.authors());
        assertEquals("Acme Editor", r.publisher());
        assertEquals(2003, r.pubDate());
        assertEquals("DDD, design, software", r.subject());
        assertEquals("PDF", r.format());
        // Day-one 不渲染 PDF 首页，cover=null
        assertNull(r.embeddedCover());
    }

    @Test
    void splitsMultiAuthorWithSemicolon(@TempDir Path tempDir) throws Exception {
        Path file = TestBookFactory.writeMinimalPdf(tempDir, "two.pdf");
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            doc.getDocumentInformation().setAuthor("Alice; Bob");
            doc.save(file.toFile());
        }
        BookResult r = parser.parse(file);
        assertEquals("Alice & Bob", r.authors());
    }

    @Test
    void titleFallsBackToFileName(@TempDir Path tempDir) throws Exception {
        Path file = TestBookFactory.writeMinimalPdf(tempDir, "no-info.pdf");
        BookResult r = parser.parse(file);
        assertEquals("no-info", r.title());
    }

    @Test
    void corruptedFileFallsBack(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("not-a-pdf.pdf");
        java.nio.file.Files.writeString(file, "not a pdf");
        BookResult r = parser.parse(file);
        assertTrue(r.parseError());
        assertEquals("not-a-pdf", r.title());
    }

    @Test
    void parsePubDateExtractsYear() {
        assertEquals(2023, PdfBookParser.parsePubDate("2023-05-15"));
        assertEquals(2023, PdfBookParser.parsePubDate("D:20230515000000+08'00'"));
        assertEquals(2023, PdfBookParser.parsePubDate("2023"));
        assertNull(PdfBookParser.parsePubDate("20"));
        assertNull(PdfBookParser.parsePubDate(null));
    }

    @Test
    void splitAuthorsNullSafe() {
        assertNull(PdfBookParser.splitAuthors(null));
        assertEquals("Alice", PdfBookParser.splitAuthors("Alice"));
    }
}