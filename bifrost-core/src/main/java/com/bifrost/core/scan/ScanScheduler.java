package com.bifrost.core.scan;

import com.bifrost.common.constant.ErrorCodes;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.audio.MusicScanService;
import com.bifrost.core.book.BookScanService;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.event.ScanStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 定时扫描调度（{@code bifrost.scan.cron}）：把"往媒体目录丢文件 → 自动进库"这件事
 * 交给后台，而不必依赖管理端点手动触发（容器化/无头部署的前提）。
 *
 * <p>音乐与图书各自独立调用，复用 {@link MusicScanService} / {@link BookScanService}
 * 已有的互斥锁与批事务——两者物理隔开（ADR-0004），一个卡住不影响另一个。
 * 已有扫描在跑时<b>跳过而不是排队</b>（下次 cron 再来），与 M3-sync 自动扫描的取舍一致。</p>
 *
 * <p>{@code cron} 为 6 段（含秒，Spring {@code CronExpression} 要求），例如
 * {@code 0 0 3 * * *} = 每天 03:00:00；留空则整个调度器不注册（{@code @ConditionalOnExpression}）。
 * 时区取 JVM 默认时区，容器里请设 {@code TZ}（compose 已设 Asia/Shanghai）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${bifrost.scan.cron:}'.trim().length() > 0")
public class ScanScheduler {

    private final MusicScanService musicScanService;
    private final BookScanService bookScanService;
    private final BifrostProperties properties;

    /** 定时扫描：音乐 → 图书，串行；单个失败不影响另一个。 */
    @Scheduled(cron = "${bifrost.scan.cron}")
    public void scanAll() {
        log.info("定时扫描开始（cron={}）", properties.getScan().getCron());
        scan("音乐", () -> musicScanService.scanAll(false));
        scan("图书", () -> bookScanService.scanAll(false));
        log.info("定时扫描结束");
    }

    private void scan(String label, Supplier<ScanStats> action) {
        try {
            ScanStats stats = action.get();
            log.info("定时扫描完成（{}）：新增 {} / 更新 {} / 缺失 {} / 失败 {}",
                    label, stats.added(), stats.updated(), stats.missing(), stats.error());
        } catch (BizException e) {
            if (e.getCode() == ErrorCodes.SCAN_IN_PROGRESS) {
                log.info("定时扫描跳过（{}）：已有扫描进行中", label);
            } else {
                log.warn("定时扫描失败（{}）：{}", label, e.getMessage(), e);
            }
        } catch (RuntimeException e) {
            log.warn("定时扫描异常（{}）", label, e);
        }
    }
}
