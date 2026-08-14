package com.bifrost.bootstrap;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 扫描集成测试用迷你媒体样本工厂（bootstrap 测试作用域；与 core 测试工厂同构，避免 test-jar 依赖）。
 */
final class MiniMediaFactory {

    private MiniMediaFactory() {
    }

    /** 生成带标签的 MP3（100 帧静音 ≈ 2.6s）。 */
    static Path writeMp3(Path dir, String fileName, String title, String artist, String albumArtist,
                         String album, int trackNo, int year) throws Exception {
        byte[] frame = minimalMp3Frame();
        byte[] data = new byte[frame.length * 100];
        for (int i = 0; i < 100; i++) {
            System.arraycopy(frame, 0, data, i * frame.length, frame.length);
        }
        Path file = dir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.write(file, data);
        writeTags(file, title, artist, albumArtist, album, trackNo, year);
        return file;
    }

    /** 生成带标签的 FLAC。 */
    static Path writeFlac(Path dir, String fileName, String title, String artist, String albumArtist,
                          String album, int trackNo, int year) throws Exception {
        Path file = dir.resolve(fileName);
        byte[] flac = new byte[42 + 4096];
        System.arraycopy("fLaC".getBytes(), 0, flac, 0, 4);
        // STREAMINFO（34 字节）：44.1k/2ch/16bit/0 samples
        flac[4] = 0x00;
        flac[5] = 0x00;
        flac[6] = 0x00;
        flac[7] = 0x22;
        flac[8] = 0x10;
        flac[9] = 0x00;
        flac[10] = 0x10;
        flac[11] = 0x00;
        long bits = (44100L << 44) | ((2L - 1) << 41) | ((16L - 1) << 36);
        for (int i = 0; i < 8; i++) {
            flac[12 + i] = (byte) (bits >>> (56 - 8 * i));
        }
        // PADDING（type 1，last=1，len 4096）
        flac[42] = (byte) 0x81;
        Files.createDirectories(file.getParent());
        Files.write(file, flac);
        writeTags(file, title, artist, albumArtist, album, trackNo, year);
        return file;
    }

    private static void writeTags(Path file, String title, String artist, String albumArtist,
                                  String album, int trackNo, int year) throws Exception {
        AudioFile audioFile = AudioFileIO.read(file.toFile());
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        if (title != null) {
            tag.setField(FieldKey.TITLE, title);
        }
        if (artist != null) {
            tag.setField(FieldKey.ARTIST, artist);
        }
        if (albumArtist != null) {
            tag.setField(FieldKey.ALBUM_ARTIST, albumArtist);
        }
        if (album != null) {
            tag.setField(FieldKey.ALBUM, album);
        }
        tag.setField(FieldKey.TRACK, String.valueOf(trackNo));
        tag.setField(FieldKey.YEAR, String.valueOf(year));
        AudioFileIO.write(audioFile);
    }

    private static byte[] minimalMp3Frame() {
        byte[] frame = new byte[417];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        frame[2] = (byte) 0x90;
        frame[3] = 0x00;
        return frame;
    }
}
