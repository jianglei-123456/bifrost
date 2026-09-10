package com.bifrost.api.dto.music;

import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;

import java.time.Instant;

/**
 * 音乐目录 DTO（与 book 侧共享 {@link LibraryRoot} 实体；仅展示用，不复写 API）。
 *
 * <p>镜像 {@code com.bifrost.api.dto.book.BookRootDto}，另带 {@code lastScanStats}
 * （管理端「上次统计」列与总览页需要）。</p>
 */
public record MusicRootDto(
        Long id,
        String name,
        String path,
        Boolean enabled,
        MediaType mediaType,
        Instant lastScanAt,
        ScanStatus scanStatus,
        String lastScanStats) {

    public static MusicRootDto of(LibraryRoot r) {
        return new MusicRootDto(r.getId(), r.getName(), r.getPath(), r.getEnabled(),
                r.getMediaType(), r.getLastScanAt(), r.getScanStatus(), r.getLastScanStats());
    }
}
