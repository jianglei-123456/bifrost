package com.bifrost.api.dto;

import com.bifrost.domain.entity.LibraryRoot;

import java.util.List;

/**
 * 扫描状态视图（GET /api/scan/status）。
 */
public record ScanStatusView(boolean scanning, List<LibraryRoot> roots) {
}
