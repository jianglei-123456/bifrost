package com.bifrost.api.dto;

import java.time.Instant;

/**
 * 艺术家视图（管理 REST 列表项；含 albumCount 聚合值）。
 */
public record ArtistView(Long id, String name, String indexLetter, long albumCount,
                         Instant starredAt, Integer rating, Integer playCount, Instant lastPlayed) {
}
