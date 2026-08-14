package com.bifrost.adapter.syrinx.subsonic.service;

import com.bifrost.core.audio.CoverService;
import com.bifrost.core.event.ScanCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 扫描完成事件订阅（《通用功能说明》§12 / T5.8）：清除封面缩略图缓存（封面可能已更新）。
 */
@Component
@RequiredArgsConstructor
public class ScanCompletedEventListener {

    private final CoverService coverService;

    @EventListener
    public void onScanCompleted(ScanCompletedEvent event) {
        coverService.invalidateAllThumbnails();
    }
}
