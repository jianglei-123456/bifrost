package com.bifrost.adapter.opds.service;

import com.bifrost.adapter.opds.OpdsConstants;
import com.bifrost.domain.entity.Book;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpdsFeedBuilder 单元测试：模板结构、XML 转义、cover link 条件、recent sort/new link、多作者拆分。
 */
class OpdsFeedBuilderTest {

    private final OpdsFeedBuilder builder = new OpdsFeedBuilder();

    @Test
    void catalogRootHasTwoEntries() {
        String xml = builder.buildCatalogRoot(Instant.now());
        assertTrue(xml.contains("<id>urn:bifrost:opds:catalog</id>"));
        assertTrue(xml.contains("<title>Bifrost 图书库</title>"));
        // 2 个 entry（all + recent）
        int entries = xml.split("<entry>", -1).length - 1;
        assertTrue(entries >= 2, "expected at least 2 entries, got " + entries);
        assertTrue(xml.contains("/catalog/all"));
        assertTrue(xml.contains("/catalog/recent"));
        assertTrue(xml.contains("/search.xml"));
    }

    @Test
    void entryEscapesUserInput() {
        Book b = new Book();
        b.setId(1L);
        b.setTitle("S&L & the \"Magic\" <Tag>");
        b.setAuthors("Alice & Bob");
        b.setLanguage("en");
        b.setIdentifier("urn:isbn:123");
        b.setDescription("</dc:description> & attack");
        b.setPubDate(2023);
        b.setFileSize(12345L);
        b.setExtension("epub");
        b.setUpdatedAt(Instant.now());

        String xml = builder.buildEntry(b);
        // XML 11 转义：& < > " '
        assertTrue(xml.contains("S&amp;L &amp; the &quot;Magic&quot; &lt;Tag&gt;"),
                "title not escaped: " + xml);
        assertTrue(xml.contains("&lt;/dc:description&gt; &amp; attack"),
                "description not escaped: " + xml);
        // 多作者 split（"Alice & Bob" → 2 个 <author>）
        int authorCount = xml.split("<author>", -1).length - 1;
        assertTrue(authorCount == 2, "expected 2 authors, got " + authorCount);
        assertTrue(xml.contains("<name>Alice</name>"));
        assertTrue(xml.contains("<name>Bob</name>"));
        assertTrue(xml.contains(OpdsConstants.MIME_EPUB));
        assertTrue(xml.contains("length=\"12345\""));
    }

    @Test
    void entryOmitsCoverLinkWhenCoverSourceNull() {
        Book b = new Book();
        b.setId(2L);
        b.setTitle("No Cover Book");
        b.setAuthors("Alice");
        b.setExtension("pdf");
        b.setFileSize(100L);
        b.setUpdatedAt(Instant.now());
        b.setCoverSource(null);  // PDF 无 cover

        String xml = builder.buildEntry(b);
        assertFalse(xml.contains("rel=\"http://opds-spec.org/image\""),
                "should not include image rel when no cover: " + xml);
    }

    @Test
    void entryIncludesCoverLinkWhenCoverSourceSet() {
        Book b = new Book();
        b.setId(3L);
        b.setTitle("With Cover");
        b.setAuthors("Alice");
        b.setExtension("epub");
        b.setFileSize(100L);
        b.setUpdatedAt(Instant.now());
        b.setCoverSource("EMBEDDED");

        String xml = builder.buildEntry(b);
        assertTrue(xml.contains("rel=\"http://opds-spec.org/image\""));
        assertTrue(xml.contains("rel=\"http://opds-spec.org/image/thumbnail\""));
        assertTrue(xml.contains("/catalog/3/cover"));
    }

    @Test
    void osddContainsSearchUrl() {
        String xml = builder.buildOsdd();
        assertNotNull(xml);
        assertTrue(xml.contains("<ShortName>Bifrost 图书搜索</ShortName>"));
        assertTrue(xml.contains("template=\"" + OpdsConstants.OPDS_BASE + "/search"));
        assertTrue(xml.contains("{searchTerms}"));
    }

    @Test
    void multiAuthorSplit() {
        Book b = new Book();
        b.setId(10L);
        b.setAuthors("Alice & Bob & Charlie");
        List<String> names = builder.authorNames(b);
        assertTrue(names.size() == 3);
        assertTrue(names.contains("Alice"));
        assertTrue(names.contains("Bob"));
        assertTrue(names.contains("Charlie"));
    }
}