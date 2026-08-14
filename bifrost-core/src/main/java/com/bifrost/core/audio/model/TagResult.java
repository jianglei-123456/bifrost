package com.bifrost.core.audio.model;

/**
 * 标签解析结果（归一化后）。
 *
 * <p>约定：字符串已 trim（空值置 null）；year 解析失败置 null；解析异常返回仅含文件名的兜底结果
 * （parseError=true）。见《音乐管理技术设计》§3。</p>
 *
 * @param title          曲目标题（标签缺失时用文件名兜底）
 * @param trackNo        曲目号（默认 1）
 * @param discNo         碟号（默认 1）
 * @param artistName     曲目艺术家（可空）
 * @param albumArtistName 专辑艺术家（albumArtist 缺省回退 track artist）
 * @param albumTitle     专辑标题（可空）
 * @param year           发行年份（可空）
 * @param genre          流派（取首个，可空）
 * @param durationSeconds 时长（秒，可空）
 * @param bitrate        码率 kbps（可空）
 * @param sampleRate     采样率 Hz（可空）
 * @param embeddedCover  内嵌封面图原始字节（可空）
 * @param parseError     是否解析失败（仅文件名兜底）
 */
public record TagResult(
        String title,
        Integer trackNo,
        Integer discNo,
        String artistName,
        String albumArtistName,
        String albumTitle,
        Integer year,
        String genre,
        Integer durationSeconds,
        Integer bitrate,
        Integer sampleRate,
        byte[] embeddedCover,
        boolean parseError) {

    /** 解析失败的兜底结果：仅文件名标题，其余为空。 */
    public static TagResult fallback(String fileNameWithoutExtension) {
        return new TagResult(fileNameWithoutExtension, 1, 1, null, null, null, null, null, null, null, null, null, true);
    }
}
