package com.bifrost.core.book.sync;

import com.bifrost.core.book.BookScanService;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.event.ScanStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 未匹配的新文档指纹 → 触发一次图书扫描（M3-sync T1.5，R5/C1）。
 *
 * <p>语义（用户决策）：收到新进度但匹配不到图书时，给"书还没入库"留<b>一次</b>机会；
 * 扫描后仍未命中即固定为孤儿，此后只能人工绑定。</p>
 *
 * <p>护栏：</p>
 * <ul>
 *   <li><b>无冷却</b>；去重靠调用方——只在（账号, 指纹）<b>首次建行</b>时触发，同一指纹永不重复触发；</li>
 *   <li>正在扫描 → <b>跳过、不排队</b>（避免扫描风暴）；</li>
 *   <li>后台异步执行（客户端进度推送超时只有 2/5 秒，<b>绝不在请求里扫库</b>）；</li>
 *   <li>任何异常都吞掉并记日志，不冒泡到 HTTP 请求线程。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnmatchedProgressScanTrigger {

    private final BookScanService bookScanService;
    private final BifrostProperties properties;

    /** 触发一次后台增量扫描（开关见 {@code bifrost.kosync.auto-scan-on-unmatched}）。 */
    public void triggerAsync() {
        if (!properties.getKosync().isAutoScanOnUnmatched()) {
            return;
        }
        if (bookScanService.isScanning()) {
            log.debug("图书扫描已在运行，跳过未匹配进度触发的自动扫描");
            return;
        }
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    ScanStats stats = bookScanService.scanAll(false);
                    log.info("未匹配进度触发的图书扫描完成: added={} updated={} missing={} error={}",
                            stats.added(), stats.updated(), stats.missing(), stats.error());
                } catch (RuntimeException e) {
                    // 竞态下仍可能撞上"扫描进行中"(1100) —— 属于正常情况，不重试、不排队
                    log.info("未匹配进度触发的图书扫描未执行: {}", e.toString());
                }
            });
        } catch (RuntimeException e) {
            log.warn("调度未匹配进度自动扫描失败: {}", e.toString());
        }
    }
}
