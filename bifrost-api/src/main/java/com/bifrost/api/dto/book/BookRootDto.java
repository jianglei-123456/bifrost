package com.bifrost.api.dto.book;

import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;

import java.time.Instant;

/**
 * 图书库根 DTO（与 music LibraryRoot 共享 entity；仅展示用，不复写 API）。
 */
public record BookRootDto(
        Long id,
        String name,
        String path,
        Boolean enabled,
        MediaType mediaType,
        Instant lastScanAt,
        ScanStatus scanStatus) {

    public static BookRootDto of(LibraryRoot r) {
        return new BookRootDto(r.getId(), r.getName(), r.getPath(), r.getEnabled(),
                r.getMediaType(), r.getLastScanAt(), r.getScanStatus());
    }
}