package com.bifrost.adapter.opds;

/**
 * OPDS 端点常量与 MIME 映射（M2-book，{@code doc/m2-book/task/02-OPDS发布.md}）。
 */
public final class OpdsConstants {

    private OpdsConstants() {
    }

    public static final String OPDS_BASE = "/opds/v1.2";

    /** Atom navigation feed（目录/索引） */
    public static final String MIME_NAVIGATION = "application/atom+xml;profile=opds-catalog;kind=navigation";

    /** Atom acquisition feed（条目/列表） */
    public static final String MIME_ACQUISITION = "application/atom+xml;profile=opds-catalog;kind=acquisition";

    /** OSDD */
    public static final String MIME_OSDD = "application/opensearchdescription+xml";

    /** 文件下载 MIME（按扩展名映射） */
    public static final String MIME_EPUB = "application/epub+zip";

    public static final String MIME_PDF = "application/pdf";

    /** 分页默认值与边界（Q30：count 默认 50，限制 10–200） */
    public static final int DEFAULT_PAGE = 1;

    public static final int DEFAULT_COUNT = 50;

    public static final int MIN_COUNT = 10;

    public static final int MAX_COUNT = 200;

    /** OPDS 关系命名空间（spec 1.2） */
    public static final String REL_SUBSECTION = "subsection";

    public static final String REL_ACQUISITION = "http://opds-spec.org/acquisition";

    public static final String REL_IMAGE = "http://opds-spec.org/image";

    public static final String REL_IMAGE_THUMBNAIL = "http://opds-spec.org/image/thumbnail";

    public static final String REL_SORT_NEW = "http://opds-spec.org/sort/new";

    public static final String REL_SEARCH = "search";

    public static final String REL_START = "start";

    public static final String REL_SELF = "self";

    public static final String REL_NEXT = "next";

    public static final String REL_PREV = "prev";

    public static String mimeForFile(String extension) {
        if (extension == null) {
            return "application/octet-stream";
        }
        return switch (extension.toLowerCase()) {
            case "epub", "kepub.epub" -> MIME_EPUB;
            case "pdf" -> MIME_PDF;
            default -> "application/octet-stream";
        };
    }
}