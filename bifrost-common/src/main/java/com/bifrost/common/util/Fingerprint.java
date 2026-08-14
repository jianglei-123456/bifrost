package com.bifrost.common.util;

import java.nio.file.Path;

/**
 * 变更检测指纹工具。
 *
 * <p>指纹 = 路径 + 文件大小 + 最后修改时间（mtime），见《通用功能说明》§6.4；
 * 指纹一致则跳过标签解析（增量扫描性能关键）。</p>
 */
public final class Fingerprint {

    private Fingerprint() {
    }

    /** 由文件计算指纹。 */
    public static String of(Path file) {
        return of(file.toAbsolutePath().normalize().toString(), FileIO.size(file), FileIO.lastModifiedMillis(file));
    }

    /** 由组成要素计算指纹（新增/变更比对用）。 */
    public static String of(String path, long size, long lastModifiedMillis) {
        return path + "|" + size + "|" + lastModifiedMillis;
    }
}
