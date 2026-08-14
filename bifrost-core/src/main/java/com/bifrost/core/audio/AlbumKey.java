package com.bifrost.core.audio;

import com.bifrost.common.util.Strings;

/**
 * 专辑聚合键（Album Key）。
 *
 * <p>键 = normalize(albumArtistName) + "|" + normalize(title)；
 * normalize = trim + 小写折叠。albumArtist 缺省时由调用方以 track artist 回退。
 * 见《音乐管理功能说明》§4.2。</p>
 */
public final class AlbumKey {

    private AlbumKey() {
    }

    /** 聚合键；任一部分为 null 时以空串参与拼接（无署名专辑也可聚合）。 */
    public static String key(String albumArtistName, String title) {
        String artist = Strings.normalize(albumArtistName);
        String album = Strings.normalize(title);
        return (artist == null ? "" : artist) + "|" + (album == null ? "" : album);
    }

    /** 聚合键比对（忽略大小写与首尾空白）。 */
    public static boolean matches(String albumArtistName, String title, String otherArtist, String otherTitle) {
        return key(albumArtistName, title).equals(key(otherArtist, otherTitle));
    }
}
