package com.bifrost.core.audio;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.StandardArtwork;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 测试媒体样本工厂：程序化生成最小 MP3/FLAC/M4A 文件并写入标签（测试用，与生产只读解析无关）。
 */
final class TestMediaFactory {

    private TestMediaFactory() {
    }

    /** 标签规格 */
    record TagSpec(String title, String artist, String albumArtist, String album,
                   Integer trackNo, Integer discNo, Integer year, String genre, byte[] cover) {

        static TagSpec of(String title, String artist, String albumArtist, String album, int trackNo) {
            return new TagSpec(title, artist, albumArtist, album, trackNo, 1, 2000, "Pop", null);
        }
    }

    /** 生成带标签的 MP3（约 100 帧静音 ≈ 2.6s）。 */
    static Path writeMp3(Path dir, String fileName, TagSpec spec) throws Exception {
        byte[] frame = minimalMp3Frame();
        byte[] data = new byte[frame.length * 100];
        for (int i = 0; i < 100; i++) {
            System.arraycopy(frame, 0, data, i * frame.length, frame.length);
        }
        Path file = dir.resolve(fileName);
        Files.write(file, data);
        writeTags(file, spec);
        return file;
    }

    /** 生成带标签的 FLAC（fLaC + STREAMINFO，无音频帧）。 */
    static Path writeFlac(Path dir, String fileName, TagSpec spec) throws Exception {
        Path file = dir.resolve(fileName);
        Files.write(file, minimalFlac());
        writeTags(file, spec);
        return file;
    }

    /** ffmpeg 是否可用（M4A 样本生成依赖；不可用时相关测试跳过）。 */
    private static final boolean FFMPEG_AVAILABLE = isFfmpegAvailable();

    static boolean ffmpegAvailable() {
        return FFMPEG_AVAILABLE;
    }

    private static boolean isFfmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 生成带标签的 M4A（ffmpeg 生成 1s AAC，jaudiotagger 写标签）。 */
    static Path writeM4a(Path dir, String fileName, TagSpec spec) throws Exception {
        if (!FFMPEG_AVAILABLE) {
            throw new IllegalStateException("ffmpeg 不可用，无法生成 M4A 样本");
        }
        Path file = dir.resolve(fileName);
        Process process = new ProcessBuilder("ffmpeg", "-y", "-f", "lavfi",
                "-i", "sine=frequency=440:duration=1", "-c:a", "aac", "-b:a", "64k",
                file.toAbsolutePath().toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("ffmpeg 生成 M4A 失败: " + output);
        }
        writeTags(file, spec);
        return file;
    }

    /** 无标签文件（如 wav）。 */
    static Path writePlain(Path dir, String fileName, byte[] bytes) throws Exception {
        Path file = dir.resolve(fileName);
        Files.write(file, bytes);
        return file;
    }

    private static void writeTags(Path file, TagSpec spec) throws Exception {
        AudioFile audioFile = AudioFileIO.read(file.toFile());
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        if (spec.title() != null) tag.setField(FieldKey.TITLE, spec.title());
        if (spec.artist() != null) tag.setField(FieldKey.ARTIST, spec.artist());
        if (spec.albumArtist() != null) tag.setField(FieldKey.ALBUM_ARTIST, spec.albumArtist());
        if (spec.album() != null) tag.setField(FieldKey.ALBUM, spec.album());
        if (spec.trackNo() != null) tag.setField(FieldKey.TRACK, String.valueOf(spec.trackNo()));
        if (spec.discNo() != null) tag.setField(FieldKey.DISC_NO, String.valueOf(spec.discNo()));
        if (spec.year() != null) tag.setField(FieldKey.YEAR, String.valueOf(spec.year()));
        if (spec.genre() != null) tag.setField(FieldKey.GENRE, spec.genre());
        if (spec.cover() != null) {
            Artwork artwork = new StandardArtwork();
            artwork.setBinaryData(spec.cover());
            tag.setField(artwork);
        }
        AudioFileIO.write(audioFile);
    }

    /** 最小静音 MP3 帧（MPEG-1 Layer III 128kbps 44.1kHz，417 字节）。 */
    static byte[] minimalMp3Frame() {
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = 0x00;
        return frame;
    }

    /** 最小 FLAC：fLaC + STREAMINFO（44.1kHz/2ch/16bit，0 采样）+ PADDING 块（满足最小文件尺寸校验）。 */
    static byte[] minimalFlac() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("fLaC".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[]{0x00, 0x00, 0x00, 0x22}); // STREAMINFO block，长度 34
        byte[] info = new byte[34];
        info[0] = 0x10; // min block size 4096
        info[1] = 0x00;
        info[2] = 0x10; // max block size 4096
        info[3] = 0x00;
        long bits = (44100L << 44) | ((2L - 1) << 41) | ((16L - 1) << 36) | 0L; // rate/ch/bps/samples
        for (int i = 0; i < 8; i++) {
            info[4 + i] = (byte) (bits >>> (56 - 8 * i));
        }
        // min/max frame size = 0，md5 = 0
        out.writeBytes(info);
        // PADDING 块（type 1，last=1）：4KB 占位，满足解析器最小尺寸校验并终止元数据块链
        out.writeBytes(new byte[]{(byte) 0x81, 0x00, 0x10, 0x00});
        out.writeBytes(new byte[4096]);
        return out.toByteArray();
    }

    /** 4x4 纯色 JPEG（内嵌封面用）。 */
    static byte[] tinyJpeg() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, 0x336699);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
