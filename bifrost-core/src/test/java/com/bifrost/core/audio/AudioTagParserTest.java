package com.bifrost.core.audio;

import com.bifrost.core.audio.model.TagResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标签解析单测（《音乐管理技术设计》§3；样本文件程序化生成）。
 */
class AudioTagParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesMp3FullTags() throws Exception {
        byte[] cover = TestMediaFactory.tinyJpeg();
        Path file = TestMediaFactory.writeMp3(tempDir, "song.mp3",
                new TestMediaFactory.TagSpec("以父之名", "周杰伦", "周杰伦", "叶惠美",
                        1, 1, 2003, "流行", cover));
        TagResult result = AudioTagParser.parse(file);
        assertFalse(result.parseError());
        assertEquals("以父之名", result.title());
        assertEquals("周杰伦", result.artistName());
        assertEquals("周杰伦", result.albumArtistName());
        assertEquals("叶惠美", result.albumTitle());
        assertEquals(1, result.trackNo());
        assertEquals(2003, result.year());
        assertEquals("流行", result.genre());
        assertTrue(result.durationSeconds() != null && result.durationSeconds() > 0);
        assertNotNull(result.embeddedCover());
        assertArrayEquals(cover, result.embeddedCover());
    }

    @Test
    void albumArtistFallsBackToArtist() throws Exception {
        Path file = TestMediaFactory.writeMp3(tempDir, "no-album-artist.mp3",
                TestMediaFactory.TagSpec.of("标题", "周杰伦", null, "专辑", 1));
        TagResult result = AudioTagParser.parse(file);
        assertEquals("周杰伦", result.albumArtistName());
    }

    @Test
    void titleFallsBackToFileName() throws Exception {
        Path file = TestMediaFactory.writeMp3(tempDir, "无标题曲目.mp3",
                TestMediaFactory.TagSpec.of(null, "周杰伦", "周杰伦", "专辑", 1));
        TagResult result = AudioTagParser.parse(file);
        assertEquals("无标题曲目", result.title());
    }

    @Test
    void parsesFlacVorbisTags() throws Exception {
        Path file = TestMediaFactory.writeFlac(tempDir, "track.flac",
                new TestMediaFactory.TagSpec("A Day in the Life", "The Beatles", "The Beatles", "Sgt. Pepper's",
                        6, 1, 1967, "Rock", null));
        TagResult result = AudioTagParser.parse(file);
        assertFalse(result.parseError());
        assertEquals("A Day in the Life", result.title());
        assertEquals("The Beatles", result.artistName());
        assertEquals("Sgt. Pepper's", result.albumTitle());
        assertEquals(6, result.trackNo());
        assertEquals(1967, result.year());
    }

    @Test
    void parsesM4aTags() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(TestMediaFactory.ffmpegAvailable(), "ffmpeg 不可用，跳过 M4A 样本测试");
        Path file = TestMediaFactory.writeM4a(tempDir, "track.m4a",
                new TestMediaFactory.TagSpec("Shake It Off", "Taylor Swift", "Taylor Swift", "1989",
                        1, 1, 2014, "Pop", null));
        TagResult result = AudioTagParser.parse(file);
        assertFalse(result.parseError(), "M4A 解析应成功（ffmpeg 生成样本）");
        assertEquals("Shake It Off", result.title());
        assertEquals("Taylor Swift", result.artistName());
        assertEquals("1989", result.albumTitle());
    }

    @Test
    void wavHasNoTagsFallsBack() throws Exception {
        Path file = TestMediaFactory.writePlain(tempDir, "hello.wav", new byte[]{1, 2, 3, 4});
        TagResult result = AudioTagParser.parse(file);
        assertEquals("hello", result.title());
        assertNull(result.artistName());
        assertTrue(result.parseError());
    }

    @Test
    void brokenFileFallsBackWithFileName() throws Exception {
        Path file = TestMediaFactory.writePlain(tempDir, "broken.mp3", "这不是音频文件".getBytes());
        TagResult result = AudioTagParser.parse(file);
        assertEquals("broken", result.title());
        assertTrue(result.parseError());
    }

    @Test
    void unsupportedExtensionFallsBack() throws Exception {
        Path file = TestMediaFactory.writePlain(tempDir, "note.ogg", new byte[]{1});
        TagResult result = AudioTagParser.parse(file);
        assertEquals("note", result.title());
        assertTrue(result.parseError());
    }
}
