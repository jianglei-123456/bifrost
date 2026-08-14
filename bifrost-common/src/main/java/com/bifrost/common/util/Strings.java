package com.bifrost.common.util;

import java.util.Locale;

/**
 * 字符串工具：trim / 空值归一 / 聚合键规范化。
 */
public final class Strings {

    private Strings() {
    }

    /**
     * trim 后为空串则返回 null，否则返回 trim 结果（标签空值归一）。
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 聚合键规范化：trim + 小写折叠（locale 无关）。
     *
     * <p>见《音乐管理功能说明》§4.2 normalize 规则。</p>
     */
    public static String normalize(String value) {
        String v = trimToNull(value);
        return v == null ? null : v.toLowerCase(Locale.ROOT);
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 首个字符；空串返回 null。 */
    public static Character firstChar(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value.charAt(0);
    }
}
