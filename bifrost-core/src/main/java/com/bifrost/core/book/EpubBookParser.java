package com.bifrost.core.book;

import com.bifrost.common.util.FileIO;
import com.bifrost.common.util.Strings;
import com.bifrost.core.book.model.BookResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import net.sf.jazzlib.ZipFile;
import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Date;
import nl.siegmann.epublib.domain.Identifier;
import nl.siegmann.epublib.domain.Metadata;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubReader;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EPUB 元数据解析器（{@code com.positiondev.epublib:epublib-core:3.1}）。
 *
 * <p>字段映射：{@code dc:title / dc:creator (List<Author>) / dc:language / dc:publisher /
 * dc:date (List<Date>) / dc:description / dc:subject (List<String>) / dc:identifier (List<Identifier>)}；
 * 多作者按 "firstname + lastname" 拼后用 " & " 连接（Q18 Calibre 同款）；
 * 系列优先 {@code otherProperties[calibre:series]}，fallback {@code belongs-to-collection}。
 * 封面 {@code book.getCoverImage().getData()}。
 * 解析异常 → {@link BookResult#fallback}（{@code parseError=true}）。</p>
 */
@Slf4j
@Component
public class EpubBookParser implements BookParser {

    /** EPUB 与 KEPUB 共享此解析器（KEPUB 结构上是 EPUB） */
    private static final String FORMAT_EPUB = "EPUB";

    @Override
    public boolean supports(String extension) {
        return "epub".equals(extension) || "kepub.epub".equals(extension);
    }

    @Override
    public BookResult parse(Path file) {
        String fallbackName = FileIO.fileNameWithoutExtension(file);
        // 用 ZipFile 路径（epublib 内置 net.sf.jazzlib）替代 InputStream 路径：
        // ZipFile 走中央目录读取，避免 ZipInputStream 在解析"压缩方式混合"ZIP 时
        // 出现 "EOF in header" 死循环（jazzlib 已知问题）。
        // 注意：jazzlib ZipFile 不是 AutoCloseable，必须 try/finally。
        ZipFile zip = null;
        try {
            zip = new ZipFile(file.toString());
            Book epub = new EpubReader().readEpub(zip);
            if (epub == null) {
                return BookResult.fallback(fallbackName);
            }
            Metadata meta = epub.getMetadata();
            if (meta == null) {
                return BookResult.fallback(fallbackName);
            }
            String title = meta.getFirstTitle();
            String authors = joinAuthors(meta.getAuthors());
            String language = Strings.trimToNull(meta.getLanguage());
            String publisher = firstOrNull(meta.getPublishers());
            Integer pubDate = parsePubDate(firstDateValue(meta.getDates()));
            String description = firstOrNull(meta.getDescriptions());
            String subject = joinStrings(meta.getSubjects(), "; ");
            String identifier = firstIdentifierValue(meta.getIdentifiers());
            String rights = firstOrNull(meta.getRights());
            String[] series = parseSeries(meta);
            byte[] cover = readCover(epub);

            return new BookResult(
                    (title == null || title.isEmpty()) ? fallbackName : title,
                    authors,
                    language,
                    publisher,
                    pubDate,
                    description,
                    subject,
                    identifier,
                    series[0],
                    series[1] == null ? null : Double.valueOf(series[1]),
                    rights,
                    cover,
                    FORMAT_EPUB,
                    false);
        } catch (IOException | RuntimeException e) {
            log.warn("EPUB 解析失败，使用文件名兜底: {} ({})", file, e.getMessage());
            return BookResult.fallback(fallbackName);
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    private static String firstOrNull(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String v : values) {
            String trimmed = Strings.trimToNull(v);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    /** 多作者按 "firstname + lastname" 拼（trim 跳过空）后 " & " 连接。 */
    static String joinAuthors(List<Author> authors) {
        if (authors == null || authors.isEmpty()) {
            return null;
        }
        return authors.stream()
                .map(a -> {
                    String first = Strings.trimToNull(a.getFirstname());
                    String last = Strings.trimToNull(a.getLastname());
                    if (first != null && last != null) {
                        return first + " " + last;
                    }
                    return first != null ? first : last;
                })
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(" & "));
    }

    /** 字符串列表以 separator 拼接（trim + 跳过空）。 */
    static String joinStrings(List<String> values, String separator) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(Strings::trimToNull)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(separator));
    }

    /** dc:date 取首个；返回字符串（year 在 parsePubDate 里再取前 4 位） */
    private static String firstDateValue(List<Date> dates) {
        if (dates == null || dates.isEmpty()) {
            return null;
        }
        for (Date d : dates) {
            if (d == null) {
                continue;
            }
            String v = Strings.trimToNull(d.getValue());
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** 取前 4 位 → Integer 年份（1–9999）；无法解析 → null。 */
    static Integer parsePubDate(String date) {
        if (date == null) {
            return null;
        }
        String trimmed = date.trim();
        if (trimmed.length() < 4) {
            return null;
        }
        String yearStr = trimmed.substring(0, 4);
        try {
            int year = Integer.parseInt(yearStr);
            return (year >= 1 && year <= 9999) ? year : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 取首个非空 Identifier 的 scheme:value；无 → null。 */
    private static String firstIdentifierValue(List<Identifier> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return null;
        }
        Identifier book = Identifier.getBookIdIdentifier(identifiers);
        if (book == null) {
            return null;
        }
        return Strings.trimToNull(book.getValue());
    }

    /**
     * 系列：先看 {@code otherProperties[calibre:series]}（QName localPart 匹配），
     * fallback {@code belongs-to-collection}（EPUB 3）。
     * 返回 [seriesName, seriesIndexString or null]。
     */
    private static String[] parseSeries(Metadata meta) {
        try {
            Map<QName, String> others = meta.getOtherProperties();
            if (others == null || others.isEmpty()) {
                return new String[]{null, null};
            }
            String seriesName = null;
            String seriesIndex = null;
            for (Map.Entry<QName, String> e : others.entrySet()) {
                QName qn = e.getKey();
                if (qn == null || qn.getLocalPart() == null) {
                    continue;
                }
                String local = qn.getLocalPart();
                String value = Strings.trimToNull(e.getValue());
                if (value == null) {
                    continue;
                }
                if ("calibre:series".equals(local) || "belongs-to-collection".equals(local)) {
                    seriesName = value;
                } else if ("calibre:series_index".equals(local) || "group-position".equals(local)) {
                    seriesIndex = value;
                }
            }
            return new String[]{seriesName, seriesIndex};
        } catch (Exception e) {
            return new String[]{null, null};
        }
    }

    private static byte[] readCover(Book epub) {
        try {
            Resource res = epub.getCoverImage();
            if (res == null) {
                return null;
            }
            byte[] data = res.getData();
            return (data == null || data.length == 0) ? null : data;
        } catch (IOException | RuntimeException e) {
            log.debug("读取 EPUB 内嵌封面失败: {}", e.getMessage());
            return null;
        }
    }
}