package com.bifrost.adapter.syrinx.subsonic;

/**
 * Subsonic 实体 ID 编解码（ar-/al-/tr-/pl- + 数字主键；库根为纯数字 ID）。
 *
 * <p>见《音乐管理技术设计》§6.3 与 Q15；"未知艺术家"为固定虚拟 ID ar-unknown（Q9/Q16）。</p>
 */
public final class SubsonicIds {

    /** 未知艺术家虚拟 ID（无对应实体） */
    public static final String UNKNOWN_ARTIST_ID = "ar-unknown";

    private SubsonicIds() {
    }

    public static String artist(Long id) {
        return "ar-" + id;
    }

    public static String album(Long id) {
        return "al-" + id;
    }

    public static String track(Long id) {
        return "tr-" + id;
    }

    public static String playlist(Long id) {
        return "pl-" + id;
    }

    public static boolean isArtist(String id) {
        return id != null && id.startsWith("ar-") && !UNKNOWN_ARTIST_ID.equals(id);
    }

    public static boolean isUnknownArtist(String id) {
        return UNKNOWN_ARTIST_ID.equals(id);
    }

    public static boolean isAlbum(String id) {
        return id != null && id.startsWith("al-");
    }

    public static boolean isTrack(String id) {
        return id != null && id.startsWith("tr-");
    }

    public static boolean isPlaylist(String id) {
        return id != null && id.startsWith("pl-");
    }

    /** 纯数字（库根 ID） */
    public static boolean isRoot(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static Long parseArtist(String id) {
        return isArtist(id) ? parseNumber(id.substring(3)) : null;
    }

    public static Long parseAlbum(String id) {
        return isAlbum(id) ? parseNumber(id.substring(3)) : null;
    }

    public static Long parseTrack(String id) {
        return isTrack(id) ? parseNumber(id.substring(3)) : null;
    }

    public static Long parsePlaylist(String id) {
        return isPlaylist(id) ? parseNumber(id.substring(3)) : null;
    }

    public static Long parseRoot(String id) {
        return isRoot(id) ? parseNumber(id) : null;
    }

    private static Long parseNumber(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
