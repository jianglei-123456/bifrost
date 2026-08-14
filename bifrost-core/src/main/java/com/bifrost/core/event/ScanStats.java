package com.bifrost.core.event;

/**
 * 扫描统计（ScanCompletedEvent 载荷）。
 *
 * <p>见《通用功能说明》§12：新增/更新/缺失/错误数。</p>
 */
public record ScanStats(int added, int updated, int missing, int error) {

    public static ScanStats empty() {
        return new ScanStats(0, 0, 0, 0);
    }
}
