package com.bifrost.api.dto;

import com.bifrost.domain.entity.Playlist;
import com.bifrost.domain.entity.Track;

import java.util.List;

/**
 * 歌单详情视图（含条目与曲目信息；曲目缺失时 track 为 null，条目保留）。
 */
public record PlaylistDetailView(Playlist playlist, List<PlaylistEntryView> entries) {

    /** 歌单条目视图 */
    public record PlaylistEntryView(Long entryId, Integer position, Track track) {
    }
}
