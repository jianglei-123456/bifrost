package com.bifrost.api.dto.music;

import java.util.List;

/**
 * 音乐扫描状态视图（{@code GET /api/music-roots/scan/status}）。
 *
 * <p>镜像图书侧 {@code com.bifrost.api.dto.book.BookScanStatusView}；{@code roots} 为启用的音乐目录
 * （DTO 化，不再直接序列化共享实体）。</p>
 */
public record MusicScanStatusView(boolean scanning, List<MusicRootDto> roots) {
}
