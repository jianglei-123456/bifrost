package com.bifrost.core.audio;

import com.bifrost.common.util.FileIO;
import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.model.TagResult;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.nio.file.Path;
import java.util.Set;

/**
 * 音频标签解析器（只读，绝不写回源文件）。
 *
 * <p>按扩展名分发：mp3→ID3、flac→Vorbis、m4a→MP4、wav→无标签兜底（可入库播放，不参与聚合）。
 * 解析异常：记录日志，返回仅含文件名的兜底结果，不阻塞扫描。
 * 字段抽取映射见《音乐管理功能说明》§2.2；jaudiotagger 3.0.1（ADR-0002）。</p>
 */
@Slf4j
public final class AudioTagParser {

    /** 支持的容器格式（v1） */
    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "flac", "m4a", "wav");

    private AudioTagParser() {
    }

    /**
     * 解析文件标签。
     *
     * @param file 媒体文件
     * @return 归一化后的解析结果（永不返回 null）
     */
    public static TagResult parse(Path file) {
        String ext = FileIO.extension(file);
        // wav 无标签，直接文件名兜底（仍可入库播放）
        if (ext == null || !SUPPORTED_EXTENSIONS.contains(ext) || "wav".equals(ext)) {
            return TagResult.fallback(FileIO.fileNameWithoutExtension(file));
        }
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return TagResult.fallback(FileIO.fileNameWithoutExtension(file));
            }
            AudioHeader header = audioFile.getAudioHeader();

            String title = Strings.trimToNull(tag.getFirst(FieldKey.TITLE));
            String artist = Strings.trimToNull(tag.getFirst(FieldKey.ARTIST));
            String albumArtist = Strings.trimToNull(tag.getFirst(FieldKey.ALBUM_ARTIST));
            String album = Strings.trimToNull(tag.getFirst(FieldKey.ALBUM));
            String genre = Strings.trimToNull(tag.getFirst(FieldKey.GENRE));

            Integer trackNo = parseIntOrNull(tag.getFirst(FieldKey.TRACK));
            Integer discNo = parseIntOrNull(tag.getFirst(FieldKey.DISC_NO));
            Integer year = parseIntOrNull(tag.getFirst(FieldKey.YEAR));

            Integer duration = header == null ? null : nonNegativeOrNull(header.getTrackLength());
            Integer bitrate = header == null ? null : nonNegativeOrNull(header.getBitRateAsNumber());
            Integer sampleRate = header == null ? null : nonNegativeOrNull(header.getSampleRateAsNumber());

            byte[] cover = null;
            try {
                Artwork artwork = tag.getFirstArtwork();
                if (artwork != null) {
                    cover = artwork.getBinaryData();
                }
            } catch (Exception e) {
                log.debug("读取内嵌封面失败: {}", file, e);
            }
            String lyrics = Strings.trimToNull(tag.getFirst(FieldKey.LYRICS));

            return new TagResult(
                    title != null ? title : FileIO.fileNameWithoutExtension(file),
                    trackNo != null ? trackNo : 1,
                    discNo != null ? discNo : 1,
                    artist,
                    albumArtist != null ? albumArtist : artist, // albumArtist 缺省回退 track artist
                    album,
                    year,
                    genre,
                    duration,
                    bitrate,
                    sampleRate,
                    cover,
                    lyrics,
                    false);
        } catch (Exception e) {
            log.warn("标签解析失败，使用文件名兜底: {} ({})", file, e.getMessage());
            return TagResult.fallback(FileIO.fileNameWithoutExtension(file));
        }
    }

    /** 解析整数（可含 "3/12" 曲目号形式，取斜杠前部分）；失败返回 null。 */
    private static Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.trim();
        int slash = candidate.indexOf('/');
        if (slash >= 0) {
            candidate = candidate.substring(0, slash);
        }
        try {
            return Integer.parseInt(candidate.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer nonNegativeOrNull(long value) {
        return value < 0 ? null : (int) value;
    }
}
