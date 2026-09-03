package com.bifrost.adapter.syrinx.subsonic.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Getter;
import lombok.Setter;

/**
 * Subsonic 响应信封（XML 根元素 subsonic-response；JSON 时外层包 {@code {"subsonic-response": ...}}）。
 *
 * <p>见《Subsonic_API_参考》§3：status/version/type/serverVersion/openSubsonic + error + 各端点载荷
 * （仅设置其一，null 字段不输出）。</p>
 */
@Getter
@Setter
@JacksonXmlRootElement(localName = "subsonic-response", namespace = "http://subsonic.org/restapi")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubsonicResponse {

    /** 状态：ok / failed */
    @JacksonXmlProperty(isAttribute = true)
    private String status = "ok";

    /** 服务器支持的 API 版本 */
    @JacksonXmlProperty(isAttribute = true)
    private String version;

    /** 服务器类型（OpenSubsonic） */
    @JacksonXmlProperty(isAttribute = true)
    private String type = "Bifrost";

    /** 服务器自身版本（OpenSubsonic） */
    @JacksonXmlProperty(isAttribute = true)
    private String serverVersion = "1.0.0-SNAPSHOT (M1)";

    /** 支持 OpenSubsonic（OpenSubsonic） */
    @JacksonXmlProperty(isAttribute = true)
    private boolean openSubsonic = true;

    /** 错误（status=failed 时） */
    @JacksonXmlProperty(localName = "error")
    private Error error;

    // ---- 各端点载荷（仅设置其一） ----
    @JacksonXmlProperty(localName = "license")
    private License license;
    @JacksonXmlProperty(localName = "musicFolders")
    private MusicFolders musicFolders;
    @JacksonXmlProperty(localName = "indexes")
    private Indexes indexes;
    @JacksonXmlProperty(localName = "artists")
    private Artists artists;
    @JacksonXmlProperty(localName = "directory")
    private Directory directory;
    @JacksonXmlProperty(localName = "artist")
    private ArtistID3 artist;
    @JacksonXmlProperty(localName = "album")
    private AlbumID3 album;
    @JacksonXmlProperty(localName = "song")
    private Child song;
    @JacksonXmlProperty(localName = "albumList")
    private AlbumList albumList;
    @JacksonXmlProperty(localName = "albumList2")
    private AlbumList2 albumList2;
    @JacksonXmlProperty(localName = "randomSongs")
    private RandomSongs randomSongs;
    @JacksonXmlProperty(localName = "nowPlaying")
    private NowPlaying nowPlaying;
    @JacksonXmlProperty(localName = "starred")
    private Starred starred;
    @JacksonXmlProperty(localName = "starred2")
    private Starred2 starred2;
    @JacksonXmlProperty(localName = "searchResult2")
    private SearchResult2 searchResult2;
    @JacksonXmlProperty(localName = "searchResult3")
    private SearchResult3 searchResult3;
    @JacksonXmlProperty(localName = "playlists")
    private Playlists playlists;
    @JacksonXmlProperty(localName = "playlist")
    private Playlist playlist;
    @JacksonXmlProperty(localName = "scanStatus")
    private ScanStatus scanStatus;
    @JacksonXmlProperty(localName = "user")
    private User user;
    @JacksonXmlProperty(localName = "users")
    private Users users;
    @JacksonXmlProperty(localName = "openSubsonicExtensions")
    private OpenSubsonicExtensions openSubsonicExtensions;
    @JacksonXmlProperty(localName = "bookmarks")
    private Bookmarks bookmarks;
    @JacksonXmlProperty(localName = "playQueue")
    private PlayQueue playQueue;
    @JacksonXmlProperty(localName = "lyrics")
    private Lyrics lyrics;
    @JacksonXmlProperty(localName = "lyricsList")
    private LyricsList lyricsList;

    /** 成功信封。 */
    public static SubsonicResponse ok(String apiVersion) {
        SubsonicResponse response = new SubsonicResponse();
        response.setVersion(apiVersion);
        return response;
    }

    /** 失败信封（协议错误码）。 */
    public static SubsonicResponse failed(String apiVersion, int code, String message) {
        SubsonicResponse response = new SubsonicResponse();
        response.setStatus("failed");
        response.setVersion(apiVersion);
        response.setError(new Error(code, message, null));
        return response;
    }
}
