package com.bifrost.common.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 文件 IO 工具：目录创建、扩展名、文件名、大小/修改时间读取。
 */
public final class FileIO {

    private FileIO() {
    }

    /** 确保目录存在（不存在则创建，含父目录）。 */
    public static void ensureDirs(Path... dirs) {
        for (Path dir : dirs) {
            if (dir == null) {
                continue;
            }
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException("创建目录失败: " + dir, e);
            }
        }
    }

    /** 文件扩展名（小写、不含点）；无扩展名返回 null。 */
    public static String extension(Path file) {
        return extension(file.getFileName() == null ? null : file.getFileName().toString());
    }

    /** 文件扩展名（小写、不含点）；无扩展名返回 null。 */
    public static String extension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 文件名去扩展名。 */
    public static String fileNameWithoutExtension(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 文件大小（字节），读取失败抛 {@link UncheckedIOException}。 */
    public static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件大小失败: " + file, e);
        }
    }

    /** 文件最后修改时间（毫秒），读取失败抛 {@link UncheckedIOException}。 */
    public static long lastModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件修改时间失败: " + file, e);
        }
    }
}
