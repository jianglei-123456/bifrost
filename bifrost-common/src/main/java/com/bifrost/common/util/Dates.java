package com.bifrost.common.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 日期工具：Instant ↔ 毫秒、ISO-8601 输出/解析。
 *
 * <p>持久化约定：时间统一以毫秒 INTEGER 存储（便于排序），见《音乐管理技术设计》§2.2；
 * 对外输出统一 ISO-8601 UTC（《通用功能说明》§9.1）。</p>
 */
public final class Dates {

    /** ISO-8601 UTC，如 2026-08-14T00:00:00Z */
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private Dates() {
    }

    /** Instant → 毫秒（null 安全）。 */
    public static Long toEpochMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    /** 毫秒 → Instant（null 安全）。 */
    public static Instant fromEpochMillis(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    /** Instant → ISO-8601 UTC 字符串（null 安全，无毫秒精度）。 */
    public static String formatIso(Instant instant) {
        return instant == null ? null : ISO_UTC.format(instant);
    }

    /** ISO-8601 字符串 → Instant（null/空白安全，解析失败返回 null）。 */
    public static Instant parseIso(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
