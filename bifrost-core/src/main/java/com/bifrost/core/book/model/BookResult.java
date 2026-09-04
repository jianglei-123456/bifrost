package com.bifrost.core.book.model;

import com.bifrost.common.util.Strings;

/**
 * 图书解析结果（归一化后）。
 *
 * <p>约定（{@code doc/m2-book/task/01-图书核心.md} T1.2）：
 * 字符串已 trim（空值置 null）；{@code pubDate} 取 {@code dc:date} 前 4 位（null=无法解析）；
 * 解析异常返回仅含文件名的兜底结果（{@code parseError=true}）。
 * 多作者已用 " & " 拼接（Q18 决策，Calibre 同款），多主题 "; " 拼接。
 * {@code embeddedCover} 仅 EPUB 抽取（PDF Day-one 不渲染首页，Q31）。</p>
 */
public record BookResult(
        String title,
        String authors,
        String language,
        String publisher,
        Integer pubDate,
        String description,
        String subject,
        String identifier,
        String series,
        Double seriesIndex,
        String rights,
        byte[] embeddedCover,
        String format,
        boolean parseError) {

    /** 解析失败的兜底结果：仅文件名为标题，其余为空。 */
    public static BookResult fallback(String fileNameWithoutExtension) {
        return new BookResult(
                Strings.trimToNull(fileNameWithoutExtension) == null ? "" : fileNameWithoutExtension,
                null, null, null, null, null, null, null, null, null, null,
                null, "UNKNOWN", true);
    }
}