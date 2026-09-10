package com.bifrost.core.book.sync;

import com.bifrost.core.book.event.BookScanCompletedEvent;
import com.bifrost.domain.entity.SyncAccount;
import com.bifrost.domain.repo.SyncAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 图书扫描完成 → 结算"待结算"的未匹配进度（M3-sync T1.5）。
 *
 * <p>{@link BookScanCompletedEvent} 在 M2-book 阶段是"预留、无消费者"，本里程碑成为它的第一个消费者。</p>
 *
 * <p>事件是<b>进程内同步发布</b>的（扫描线程里跑），因此这里只做很轻的活：正常情况下待结算行数是 0。
 * 结算本身只读写仓储，<b>不再调用扫描</b>，因此不会与扫描互斥锁产生死锁。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanProgressSettlementListener {

    private final OrphanProgressService orphanProgressService;
    private final SyncAccountRepository syncAccountRepository;

    @EventListener
    public void onBookScanCompleted(BookScanCompletedEvent event) {
        try {
            for (SyncAccount account : syncAccountRepository.findAll()) {
                int bound = orphanProgressService.settlePending(account.getId());
                if (bound > 0) {
                    log.info("图书扫描后结算未匹配进度: account={} 新绑定 {} 条", account.getUsername(), bound);
                }
            }
        } catch (RuntimeException e) {
            // 结算失败不影响扫描结果本身（进度下次重新匹配时仍可人工处理）
            log.warn("结算未匹配进度失败: {}", e.toString());
        }
    }
}
