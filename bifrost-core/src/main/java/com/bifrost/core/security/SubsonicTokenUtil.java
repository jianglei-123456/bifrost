package com.bifrost.core.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Subsonic 认证令牌工具（《Subsonic_API_参考》§2）。
 *
 * <p>令牌 = md5(password + salt)（UTF-8 字节，小写 hex）；恒定时间比较；
 * {@code p=enc:HEX} 仅为十六进制编码，非加密。</p>
 */
public final class SubsonicTokenUtil {

    private SubsonicTokenUtil() {
    }

    /** 计算令牌：md5(password + salt)，小写 hex。 */
    public static String token(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest((password + salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    /** 恒定时间比较（防时序侧信道）。 */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** 字节 → 小写 hex。 */
    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /** hex → 字节（非法输入抛 {@link IllegalArgumentException}）。 */
    public static byte[] unhex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    /** 解码密码参数：{@code enc:HEX} → 明文；否则原样返回。 */
    public static String decodePasswordParam(String p) {
        if (p == null) {
            return null;
        }
        if (p.startsWith("enc:")) {
            return new String(unhex(p.substring(4)), StandardCharsets.UTF_8);
        }
        return p;
    }
}
