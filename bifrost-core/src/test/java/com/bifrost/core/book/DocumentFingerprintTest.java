package com.bifrost.core.book;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档指纹算法测试（M3-sync T1.2）。
 *
 * <p>算法事实来源：{@code doc/m3-sync/调研/01-KOSync协议实证.md} §4.1——
 * 12 个采样点（偏移 0，随后 1024&lt;&lt;2k，k=0..10）各读 1024 字节，按顺序拼接后整体做一次 MD5；
 * 任一采样点越过 EOF 即停止。<b>必须二进制读</b>。</p>
 */
class DocumentFingerprintTest {

    @TempDir
    Path tempDir;

    private static String md5Of(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    @Test
    void emptyFileIsMd5OfEmptyInput() throws Exception {
        Path file = tempDir.resolve("empty.epub");
        Files.write(file, new byte[0]);

        assertThat(DocumentFingerprint.partialMd5(file)).isEqualTo(md5Of(new byte[0]));
    }

    @Test
    void smallFileEqualsFullFileMd5() throws Exception {
        // 小于 1024 字节时只有偏移 0 一个采样点，读到多少算多少 → 等于整文件 MD5
        byte[] content = "Bifrost 图书内容 partial md5 校验".getBytes(StandardCharsets.UTF_8);
        Path file = tempDir.resolve("small.epub");
        Files.write(file, content);

        assertThat(DocumentFingerprint.partialMd5(file)).isEqualTo(md5Of(content));
    }

    @Test
    void fileUpTo2048BytesStillEqualsFullFileMd5() throws Exception {
        // 偏移 0 与 1024 两个采样点刚好覆盖前 2048 字节 → 仍等于整文件 MD5
        byte[] content = new byte[2048];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        Path file = tempDir.resolve("exactly-2048.pdf");
        Files.write(file, content);

        assertThat(DocumentFingerprint.partialMd5(file)).isEqualTo(md5Of(content));
    }

    @Test
    void binaryReadKeepsCrLfBytes() throws Exception {
        // 若实现误用 Reader/文本读，\r\n 会被转换，指纹就会变
        byte[] content = {'A', 0x0D, 0x0A, 'B'};
        Path file = tempDir.resolve("crlf.txt");
        Files.write(file, content);

        assertThat(DocumentFingerprint.partialMd5(file)).isEqualTo(md5Of(content));
    }

    @Test
    void largerFileIsSampledNotHashedWhole() throws Exception {
        // 5000 字节：采样 0..1023、1024..2047、4096..4999（2048..4095 被跳过）→ 不等于整文件 MD5
        byte[] content = new byte[5000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31 % 256);
        }
        Path file = tempDir.resolve("sampled.epub");
        Files.write(file, content);

        String digest = DocumentFingerprint.partialMd5(file);

        assertThat(digest).isNotNull().isNotEqualTo(md5Of(content));
        // 确定性：同一文件多次计算结果一致
        assertThat(DocumentFingerprint.partialMd5(file)).isEqualTo(digest);
    }

    @Test
    void twelveSamplePointsAllHitOnLargeSparseFile() throws Exception {
        // 稀疏文件（不占实际磁盘）：大小 1GB + 4096 → 12 个采样点全部命中且都读到 1024 字节全 0
        Path file = tempDir.resolve("huge.bin");
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength((1L << 30) + 4096);
        }

        assertThat(DocumentFingerprint.partialMd5(file)).isEqualTo(md5Of(new byte[1024 * 12]));
    }

    @Test
    void fileJustBelowFirstRepeatedOffsetStopsSampling() throws Exception {
        // 大小恰为 1GB：最后一个采样点（偏移 1GB）越过 EOF → 只采 11 段
        Path file = tempDir.resolve("exactly-1g.bin");
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.setLength(1L << 30);
        }

        assertThat(DocumentFingerprint.partialMd5(file)).isEqualTo(md5Of(new byte[1024 * 11]));
    }

    @Test
    void missingFileReturnsNullInsteadOfThrowing() {
        assertThat(DocumentFingerprint.partialMd5(tempDir.resolve("nope.epub"))).isNull();
        assertThat(DocumentFingerprint.partialMd5(null)).isNull();
    }

    @Test
    void fileNameMd5UsesBasenameOnly() throws Exception {
        Path file = Path.of("/library/Author Name/My Book.epub");

        assertThat(DocumentFingerprint.fileNameMd5(file)).isEqualTo(md5Of("My Book.epub".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void opdsServedNameMd5UsesTitleAndExtension() throws Exception {
        assertThat(DocumentFingerprint.opdsServedNameMd5("My Book", "epub"))
                .isEqualTo(md5Of("My Book.epub".getBytes(StandardCharsets.UTF_8)));
        // 无扩展名时只用标题
        assertThat(DocumentFingerprint.opdsServedNameMd5("My Book", null))
                .isEqualTo(md5Of("My Book".getBytes(StandardCharsets.UTF_8)));
        assertThat(DocumentFingerprint.opdsServedNameMd5(" ", "epub")).isNull();
    }
}
