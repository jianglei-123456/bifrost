package com.bifrost.core.book;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文档指纹工具（M3-sync T1.2）。
 *
 * <p><b>与 {@link com.bifrost.common.util.Fingerprint}（{@code path|size|mtime}）是两套完全不同的
 * 东西</b>：本类算的是 KOSync 协议里的 {@code document}——客户端为"我手上这份文件"算出的
 * 32 位 hex 标识（见 {@code doc/m3-sync/调研/01-KOSync协议实证.md} §4）。</p>
 *
 * <p>算法（客户端 {@code util.partialMD5} 的等价实现）：在 12 个采样点各读 1024 字节，
 * 按顺序拼接后整体做一次 MD5：偏移 {@code 0}，随后 {@code 1024 << 2k}（k=0..10）
 * 即 1KB、4KB、16KB、64KB、256KB、1MB、4MB、16MB、64MB、256MB、1GB。
 * 任一采样点越过 EOF 即停止（小文件只采前几段）。<b>必须二进制读</b>——文本读会做换行转换，
 * 改变字节流。</p>
 */
@Slf4j
public final class DocumentFingerprint {

    /** 每个采样点读取的字节数 */
    private static final int SAMPLE_SIZE = 1024;

    /** 采样点偏移：0 + 1024 << 2k（k=0..10），共 12 个 */
    private static final long[] OFFSETS = buildOffsets();

    private DocumentFingerprint() {
    }

    private static long[] buildOffsets() {
        long[] offsets = new long[12];
        offsets[0] = 0L;
        for (int k = 0; k < 11; k++) {
            offsets[k + 1] = 1024L << (2 * k);
        }
        return offsets;
    }

    /**
     * 文件内容 partial MD5（小写 hex）。
     *
     * @return 32 位 hex；文件不存在/不可读/计算失败时返回 {@code null}（**不抛异常**——扫描不能
     *         因为一个文件算不出指纹而中断）
     */
    public static String partialMd5(Path file) {
        if (file == null) {
            return null;
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long size = channel.size();
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            ByteBuffer buffer = ByteBuffer.allocate(SAMPLE_SIZE);
            for (long offset : OFFSETS) {
                if (offset >= size) {
                    break;
                }
                buffer.clear();
                channel.position(offset);
                int read = channel.read(buffer);
                if (read <= 0) {
                    break;
                }
                md5.update(buffer.array(), 0, read);
            }
            return HexFormat.of().formatHex(md5.digest());
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            log.warn("计算文档指纹失败: {} ({})", file, e.toString());
            return null;
        }
    }

    /** 文件名 MD5（客户端 {@code checksum_method=FILENAME} 模式：仅 basename，不含路径）。 */
    public static String fileNameMd5(Path file) {
        Path name = file == null ? null : file.getFileName();
        return name == null ? null : md5Hex(name.toString());
    }

    /** OPDS 下发到设备时的文件名指纹：{@code md5(标题 + "." + 扩展名)}。 */
    public static String opdsServedNameMd5(String title, String extension) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String name = (extension == null || extension.isBlank()) ? title : title + "." + extension;
        return md5Hex(name);
    }

    /** 字符串 MD5（小写 hex）。 */
    public static String md5Hex(String value) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md5.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
