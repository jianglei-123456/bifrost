package com.bifrost.api.dto.booksync;

import com.bifrost.domain.entity.SyncDevice;

import java.time.Instant;

/**
 * 同步设备视图（M3-sync T3.5，R6）。
 *
 * <p>只读：协议层没有会话，"踢设备"做不出真效果（Q5 决策）——要让设备重新登录只能改口令。</p>
 */
public record SyncDeviceView(
        Long id,
        String deviceId,
        String deviceName,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Long reportCount) {

    public static SyncDeviceView of(SyncDevice device) {
        return new SyncDeviceView(device.getId(), device.getDeviceId(), device.getDeviceName(),
                device.getFirstSeenAt(), device.getLastSeenAt(), device.getReportCount());
    }
}
