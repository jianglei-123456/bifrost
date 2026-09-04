package com.bifrost.adapter.opds.service;

import com.bifrost.adapter.opds.OpdsConstants;
import com.bifrost.domain.entity.Book;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OPDS Atom XML 模板构建器（M2-book，T2.2）。
 *
 * <p>使用 inline string template + {@link StringEscapeUtils#escapeXml11} 对所有用户输入字段转义；
 * 不引入 ROME 等第三方 Atom 库，依赖最小化（Q25 决策）。</p>
 */
@Slf4j
@Component
public class OpdsFeedBuilder {

    private static final DateTimeFormatter ATOM_TS = DateTimeFormatter.ISO_INSTANT;

    /** Atom 命名空间 */
    private static final String NS_ATOM = "http://www.w3.org/2005/Atom";
    private static final String NS_DC = "http://purl.org/dc/elements/1.1/";
    private static final String NS_OPDS = "http://opds-spec.org/2010/catalog";
    private static final String NS_OPENSEARCH = "http://a9.com/-/spec/opensearch/1.1/";

    /** 目录根 navigation feed（Q19-C：仅 2 个子条目 all + recent）。 */
    public String buildCatalogRoot(Instant serverTime) {
        String updated = formatTs(serverTime);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <feed xmlns="%s">
                  <id>urn:bifrost:opds:catalog</id>
                  <title>Bifrost 图书库</title>
                  <updated>%s</updated>
                  <link rel="%s" href="%s/catalog" type="%s"/>
                  <link rel="%s" href="%s/catalog" type="%s"/>
                  <link rel="%s" href="%s/search.xml" type="%s"/>
                  <entry>
                    <id>urn:bifrost:opds:nav:all</id>
                    <title>全部图书</title>
                    <updated>%s</updated>
                    <link rel="%s" href="%s/catalog/all" type="%s"/>
                  </entry>
                  <entry>
                    <id>urn:bifrost:opds:nav:recent</id>
                    <title>最近添加</title>
                    <updated>%s</updated>
                    <link rel="%s" href="%s/catalog/recent" type="%s"/>
                  </entry>
                </feed>
                """.formatted(
                NS_ATOM,
                updated,
                OpdsConstants.REL_SELF, OpdsConstants.OPDS_BASE, OpdsConstants.MIME_NAVIGATION,
                OpdsConstants.REL_START, OpdsConstants.OPDS_BASE, OpdsConstants.MIME_NAVIGATION,
                OpdsConstants.REL_SEARCH, OpdsConstants.OPDS_BASE, OpdsConstants.MIME_OSDD,
                updated,
                OpdsConstants.REL_SUBSECTION, OpdsConstants.OPDS_BASE, OpdsConstants.MIME_ACQUISITION,
                updated,
                OpdsConstants.REL_SUBSECTION, OpdsConstants.OPDS_BASE, OpdsConstants.MIME_ACQUISITION);
    }

    /**
     * acquisition feed（all / recent / search 共用；{@code isRecent=true} 时额外挂 sort/new link）。
     *
     * @param feedId   feed 全局唯一 ID（如 {@code urn:bifrost:opds:all:page:1}）
     * @param title    feed 标题
     * @param page     分页结果（1-based page 已映射到 0-based）
     * @param path     self link 路径（如 {@code /opds/v1.2/catalog/all}）
     * @param isRecent 是否 recent feed
     */
    public String buildAcquisitionFeed(String feedId, String title, Page<Book> page, String path, boolean isRecent) {
        Instant now = Instant.now();
        int pageNum = page.getNumber() + 1;
        int count = page.getSize();
        long total = page.getTotalElements();
        int startIndex = (pageNum - 1) * count + 1;

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<feed xmlns=\"").append(NS_ATOM).append("\"")
                .append(" xmlns:dc=\"").append(NS_DC).append("\"")
                .append(" xmlns:opds=\"").append(NS_OPDS).append("\"")
                .append(" xmlns:opensearch=\"").append(NS_OPENSEARCH).append("\">\n");
        xml.append("  <id>").append(esc(feedId)).append("</id>\n");
        xml.append("  <title>").append(esc(title)).append("</title>\n");
        xml.append("  <updated>").append(formatTs(now)).append("</updated>\n");
        xml.append("  <opensearch:totalResults>").append(total).append("</opensearch:totalResults>\n");
        xml.append("  <opensearch:startIndex>").append(startIndex).append("</opensearch:startIndex>\n");
        xml.append("  <opensearch:itemsPerPage>").append(count).append("</opensearch:itemsPerPage>\n");
        xml.append("  <link rel=\"self\" href=\"")
                .append(esc(path)).append("?page=").append(pageNum).append("&amp;count=").append(count)
                .append("\" type=\"").append(OpdsConstants.MIME_ACQUISITION).append("\"/>\n");
        // recent 标 sort/new（Readest "Auto-download new items" 钩子）
        if (isRecent) {
            xml.append("  <link rel=\"").append(OpdsConstants.REL_SORT_NEW)
                    .append("\" href=\"").append(esc(path))
                    .append("\" type=\"").append(OpdsConstants.MIME_ACQUISITION).append("\"/>\n");
        }
        if (page.hasNext()) {
            int next = pageNum + 1;
            xml.append("  <link rel=\"next\" href=\"")
                    .append(esc(path)).append("?page=").append(next).append("&amp;count=").append(count)
                    .append("\" type=\"").append(OpdsConstants.MIME_ACQUISITION).append("\"/>\n");
        }
        if (page.hasPrevious()) {
            int prev = pageNum - 1;
            xml.append("  <link rel=\"prev\" href=\"")
                    .append(esc(path)).append("?page=").append(prev).append("&amp;count=").append(count)
                    .append("\" type=\"").append(OpdsConstants.MIME_ACQUISITION).append("\"/>\n");
        }

        for (Book b : page.getContent()) {
            xml.append(buildEntry(b));
        }
        xml.append("</feed>\n");
        return xml.toString();
    }

    /** 单个 book 的 entry（acquisition feed 内的 &lt;entry&gt;）。 */
    public String buildEntry(Book b) {
        StringBuilder e = new StringBuilder();
        e.append("  <entry>\n");
        e.append("    <id>urn:bifrost:book:").append(b.getId()).append("</id>\n");
        e.append("    <title>").append(esc(b.getTitle())).append("</title>\n");
        e.append("    <updated>").append(formatTs(b.getUpdatedAt())).append("</updated>\n");
        // 多作者按 " & " split
        if (b.getAuthors() != null && !b.getAuthors().isBlank()) {
            for (String name : b.getAuthors().split("&")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    e.append("    <author><name>").append(esc(trimmed)).append("</name></author>\n");
                }
            }
        }
        if (b.getLanguage() != null) {
            e.append("    <dc:language>").append(esc(b.getLanguage())).append("</dc:language>\n");
        }
        if (b.getIdentifier() != null) {
            e.append("    <dc:identifier>").append(esc(b.getIdentifier())).append("</dc:identifier>\n");
        }
        if (b.getPublisher() != null) {
            e.append("    <dc:publisher>").append(esc(b.getPublisher())).append("</dc:publisher>\n");
        }
        if (b.getDescription() != null) {
            e.append("    <dc:description>").append(esc(b.getDescription())).append("</dc:description>\n");
        }
        if (b.getPubDate() != null) {
            e.append("    <dc:date>").append(b.getPubDate()).append("</dc:date>\n");
        }
        // 封面：coverSource != null 才挂 image link（PDF 无 cover 不挂，避免 Readest 拿 404）
        if (b.getCoverSource() != null) {
            e.append("    <link rel=\"")
                    .append(OpdsConstants.REL_IMAGE)
                    .append("\" href=\"").append(OpdsConstants.OPDS_BASE)
                    .append("/catalog/").append(b.getId())
                    .append("/cover\" type=\"image/jpeg\"/>\n");
            e.append("    <link rel=\"")
                    .append(OpdsConstants.REL_IMAGE_THUMBNAIL)
                    .append("\" href=\"").append(OpdsConstants.OPDS_BASE)
                    .append("/catalog/").append(b.getId())
                    .append("/cover?size=200\" type=\"image/jpeg\"/>\n");
        }
        // 主体：acquisition（下载）
        String mime = OpdsConstants.mimeForFile(b.getExtension());
        e.append("    <link rel=\"").append(OpdsConstants.REL_ACQUISITION)
                .append("\" href=\"").append(OpdsConstants.OPDS_BASE)
                .append("/catalog/").append(b.getId())
                .append("/file\" type=\"").append(mime)
                .append("\" length=\"").append(b.getFileSize() == null ? 0 : b.getFileSize())
                .append("\"/>\n");
        e.append("  </entry>\n");
        return e.toString();
    }

    /** OSDD（search.xml 端点）。 */
    public String buildOsdd() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <OpenSearchDescription xmlns="http://a9.com/-/spec/opensearch/1.1/">
                  <ShortName>Bifrost 图书搜索</ShortName>
                  <Description>搜索 Bifrost 图书库</Description>
                  <Url type="%s" template="%s/search?q={searchTerms}&amp;page={page?}&amp;count={count?}"/>
                </OpenSearchDescription>
                """.formatted(OpdsConstants.MIME_ACQUISITION, OpdsConstants.OPDS_BASE);
    }

    private static String formatTs(Instant ts) {
        if (ts == null) {
            return ATOM_TS.format(Instant.now());
        }
        return ATOM_TS.format(ts);
    }

    /** XML 11 转义（commons-text；用户字段必经此函数）。 */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return StringEscapeUtils.escapeXml11(s);
    }

    /** Used by tests: 暴露 entry builder。 */
    List<String> authorNames(Book b) {
        if (b.getAuthors() == null || b.getAuthors().isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(b.getAuthors().split("&"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}