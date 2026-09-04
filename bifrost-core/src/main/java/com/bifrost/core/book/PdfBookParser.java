package com.bifrost.core.book;

import com.bifrost.common.util.FileIO;
import com.bifrost.common.util.Strings;
import com.bifrost.core.book.model.BookResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.xmpbox.XMPMetadata;
import org.apache.xmpbox.schema.AdobePDFSchema;
import org.apache.xmpbox.schema.DublinCoreSchema;
import org.apache.xmpbox.xml.DomXmpParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;

/**
 * PDF 元数据解析器（{@code org.apache.pdfbox:pdfbox:3.0.5} + {@code xmpbox:3.0.5}）。
 *
 * <p>优先 XMP（{@link DublinCoreSchema} + {@link AdobePDFSchema}），fallback InfoDict（{@link PDDocumentInformation}）。
 * 多作者 {@code Author} 字段按 ";" split（PDF 规范允许多作者以分号分隔）；
 * {@code embeddedCover=null}（Day-one 不渲染 PDF 首页，Q31）。
 * 解析异常 → {@link BookResult#fallback}（{@code parseError=true}）。</p>
 */
@Slf4j
@Component
public class PdfBookParser implements BookParser {

    private static final String FORMAT_PDF = "PDF";

    @Override
    public boolean supports(String extension) {
        return "pdf".equals(extension);
    }

    @Override
    public BookResult parse(Path file) {
        String fallbackName = FileIO.fileNameWithoutExtension(file);
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            PDDocumentInformation info = doc.getDocumentInformation();

            // ---- 优先 XMP ----
            String xmpTitle = null, xmpCreator = null, xmpSubject = null, xmpDescription = null,
                    xmpLanguage = null, xmpPublisher = null, xmpDate = null, xmpIdentifier = null, xmpRights = null;
            PDMetadata pdMetadata = doc.getDocumentCatalog().getMetadata();
            if (pdMetadata != null) {
                try {
                    byte[] xmpBytes = pdMetadata.toByteArray();
                    if (xmpBytes != null && xmpBytes.length > 0) {
                        XMPMetadata xmp = new DomXmpParser().parse(new ByteArrayInputStream(xmpBytes));
                        DublinCoreSchema dc = xmp.getDublinCoreSchema();
                        if (dc != null) {
                            xmpTitle = trimToNull(safeGet(dc::getTitle));
                            xmpCreator = firstArrayValue(dc.getCreators());
                            xmpSubject = firstArrayValue(dc.getSubjects());
                            xmpDescription = trimToNull(safeGet(dc::getDescription));
                            xmpLanguage = firstArrayValue(dc.getLanguages());
                            xmpPublisher = firstArrayValue(dc.getPublishers());
                            xmpIdentifier = trimToNull(dc.getIdentifier());
                            xmpRights = trimToNull(safeGet(dc::getRights));
                        }
                        // AdobePDFSchema 可补充 dc:date（PDF 元数据 "pdf:Keywords" 不是 date；date 在 dc:dates）
                        if (xmp.getAdobePDFSchema() != null) {
                            // Keywords 在 AdobePDFSchema，不是日期，跳过
                        }
                    }
                } catch (Exception e) {
                    log.debug("XMP 解析失败，回退 InfoDict: {} ({})", file, e.getMessage());
                }
            }

            // ---- fallback InfoDict ----
            String infoTitle = info == null ? null : Strings.trimToNull(info.getTitle());
            String infoAuthor = info == null ? null : Strings.trimToNull(info.getAuthor());
            String infoSubject = info == null ? null : Strings.trimToNull(info.getSubject());
            String infoKeywords = info == null ? null : Strings.trimToNull(info.getKeywords());
            String infoCreator = info == null ? null : Strings.trimToNull(info.getCreator());
            String infoProducer = info == null ? null : Strings.trimToNull(info.getProducer());
            Calendar creationDate = info == null ? null : info.getCreationDate();
            Calendar modDate = info == null ? null : info.getModificationDate();

            String title = firstNonNull(xmpTitle, infoTitle, fallbackName);
            String authors = firstNonNull(
                    xmpCreator,
                    splitAuthors(infoAuthor));
            String subject = firstNonNull(
                    xmpSubject,
                    infoKeywords,
                    infoSubject);
            String description = xmpDescription;  // InfoDict 无 description 字段
            String language = xmpLanguage;
            String publisher = firstNonNull(xmpPublisher, infoCreator, infoProducer);
            Integer pubDate = firstNonNullInt(
                    parsePubDate(xmpDate),
                    parseCalYear(creationDate),
                    parseCalYear(modDate));
            String identifier = firstNonNull(xmpIdentifier, null);
            String rights = xmpRights;

            return new BookResult(
                    title,
                    authors,
                    language,
                    publisher,
                    pubDate,
                    description,
                    subject,
                    identifier,
                    null,            // series：PDF 无标准 series 字段
                    null,
                    rights,
                    null,            // embeddedCover：Day-one 不渲染 PDF 首页
                    FORMAT_PDF,
                    false);
        } catch (IOException | RuntimeException e) {
            log.warn("PDF 解析失败，使用文件名兜底: {} ({})", file, e.getMessage());
            return BookResult.fallback(fallbackName);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        String get() throws Exception;
    }

    /** xmpbox 的 getTitle/getDescription/getRights 抛 BadFieldValueException，包一层 */
    private static String safeGet(ThrowingSupplier s) {
        try {
            return s.get();
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        return value == null ? null : Strings.trimToNull(value);
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            String t = trimToNull(v);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static Integer firstNonNullInt(Integer... values) {
        for (Integer v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 取数组首个非空元素（XMP creator/publisher/subject 数组） */
    private static String firstArrayValue(List<String> values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            String t = trimToNull(v);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /** PDF InfoDict Author 多作者常见格式："Smith, J.; Doe, J." */
    static String splitAuthors(String infoAuthor) {
        if (infoAuthor == null) {
            return null;
        }
        String[] parts = infoAuthor.split("[;]");
        return java.util.Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining(" & "));
    }

    /** 前 4 位为年份；格式如 "2023-01-15..." / "2023..." / PDF date "D:20230115000000+08'00'" */
    static Integer parsePubDate(String date) {
        if (date == null) {
            return null;
        }
        String trimmed = date.trim();
        if (trimmed.length() < 4) {
            return null;
        }
        // PDF date "D:2023..." 去掉前缀
        if (trimmed.startsWith("D:") && trimmed.length() >= 6) {
            trimmed = trimmed.substring(2);
        }
        String yearStr = trimmed.substring(0, 4);
        try {
            int year = Integer.parseInt(yearStr);
            return (year >= 1 && year <= 9999) ? year : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** PDF InfoDict 的日期是 Calendar（PDF date string） */
    private static Integer parseCalYear(Calendar cal) {
        if (cal == null) {
            return null;
        }
        int year = cal.get(Calendar.YEAR);
        return (year >= 1 && year <= 9999) ? year : null;
    }
}