package com.bifrost.core.book.sync;

import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.entity.SyncDevice;
import com.bifrost.domain.enums.ProgressMatchSource;
import com.bifrost.domain.repo.ReadingProgressRepository;
import com.bifrost.domain.repo.SyncDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 阅读进度服务（M3-sync T1.4）。
 *
 * <p><b>写入是无条件后写覆盖</b>：协议语义如此（官方 2016 年 `73b9d53` 起删除了"拒绝旧进度"分支），
 * 服务端不读旧值、不比较新旧、不返回 202——新旧判断由客户端用响应里的 {@code timestamp} 完成
 * （见 {@code doc/m3-sync/调研/01-KOSync协议实证.md} §2.3/§3）。因此副作用是<b>更旧的推送也会覆盖更新的</b>，
 * 这是有意为之：客户端离线队列"发一次就丢"，服务端做"聪明"的判断只会制造不一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadingProgressService {

    private final ReadingProgressRepository progressRepository;
    private final SyncDeviceRepository deviceRepository;
    private final ProgressBookMatcher matcher;
    private final PlatformTransactionManager transactionManager;

    /** 写入结果：{@code firstAppearance} = 该（账号, 指纹）首次建行，是自动扫描的<b>唯一</b>触发条件（C1）。 */
    public record ProgressUpsert(ReadingProgress progress, boolean firstAppearance) {
    }

    /**
     * 上报进度（协议 {@code PUT /syncs/progress} 的落点）。
     *
     * <p>用 {@link TransactionTemplate} 而不是 {@code @Transactional}：并发首推会撞唯一约束，
     * 需要在<b>事务外</b>捕获异常并用**新事务**重试（撞上约束本身就是"已存在"的证据）。</p>
     */
    public ProgressUpsert upsert(Long syncAccountId, String documentFingerprint, String progress,
                                 Double percentage, String device, String deviceId) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            return tx.execute(status -> doUpsert(syncAccountId, documentFingerprint, progress, percentage,
                    device, deviceId));
        } catch (DataIntegrityViolationException e) {
            log.debug("进度并发首推撞唯一约束，重试覆盖: account={} document={}", syncAccountId, documentFingerprint);
            return tx.execute(status -> doUpsert(syncAccountId, documentFingerprint, progress, percentage,
                    device, deviceId));
        }
    }

    private ProgressUpsert doUpsert(Long syncAccountId, String documentFingerprint, String progress,
                                    Double percentage, String device, String deviceId) {
        Instant now = Instant.ofEpochSecond(Instant.now().getEpochSecond());
        ReadingProgress row = progressRepository
                .findBySyncAccountIdAndDocumentFingerprint(syncAccountId, documentFingerprint)
                .orElse(null);
        boolean firstAppearance = row == null;
        if (row == null) {
            row = new ReadingProgress();
            row.setSyncAccountId(syncAccountId);
            row.setDocumentFingerprint(documentFingerprint);
            row.setIgnored(false);
        }
        // 覆盖：bookId / scanAttemptedAt / ignored / matchSource 保持不动（它们属于"匹配状态"，不属于上报载荷）
        row.setProgress(progress);
        row.setPercentage(percentage);
        row.setDevice(device);
        row.setDeviceId(deviceId);
        row.setReportedAt(now);
        ReadingProgress saved = progressRepository.saveAndFlush(row);
        touchDevice(syncAccountId, deviceId, device, now);
        return new ProgressUpsert(saved, firstAppearance);
    }

    /** 协议读路径。 */
    public Optional<ReadingProgress> find(Long syncAccountId, String documentFingerprint) {
        return progressRepository.findBySyncAccountIdAndDocumentFingerprint(syncAccountId, documentFingerprint);
    }

    /**
     * 首次上报的<b>即时匹配</b>（M3-sync T1.5）：库里已经有这本书时直接绑定，不必等扫描。
     *
     * <p>未命中返回 {@code false}，且<b>不</b>写 {@code scanAttemptedAt}——"结算"是扫描完成后的动作，
     * 由 {@link OrphanProgressService#settlePending(Long)} 负责。</p>
     *
     * @return 命中并绑定返回 true
     */
    @Transactional
    public boolean tryAutoMatch(Long progressId) {
        ReadingProgress row = get(progressId);
        Book book = matcher.match(row.getDocumentFingerprint()).orElse(null);
        if (book == null) {
            return false;
        }
        row.setBookId(book.getId());
        row.setMatchSource(ProgressMatchSource.AUTO);
        progressRepository.save(row);
        return true;
    }

    /** 按 id 取（不存在 → 1001）。 */
    public ReadingProgress get(Long id) {
        return progressRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("进度记录不存在: " + id));
    }

    /** 待结算的未匹配进度（自动扫描完成后据此回填，M3-sync T1.5）。 */
    public List<ReadingProgress> findPendingSettlement(Long syncAccountId) {
        return progressRepository.findBySyncAccountIdAndBookIdIsNullAndScanAttemptedAtIsNull(syncAccountId);
    }

    /** 管理端列表（M3-sync T3.3）。 */
    public Page<ReadingProgress> search(Long syncAccountId, String title, String device,
                                        Long libraryRootId, boolean onlyOrphans, Pageable pageable) {
        return progressRepository.search(syncAccountId, onlyOrphans, blankToNull(title),
                libraryRootId, blankToNull(device), pageable);
    }

    /** 概览统计（M3-sync T3.5）。 */
    public Stats stats(Long syncAccountId) {
        return new Stats(
                progressRepository.countBySyncAccountId(syncAccountId),
                progressRepository.countBySyncAccountIdAndBookIdIsNotNull(syncAccountId),
                progressRepository.countBySyncAccountIdAndBookIdIsNull(syncAccountId),
                progressRepository.findLastReportedAt(syncAccountId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 概览统计载荷。 */
    public record Stats(long progressCount, long matchedCount, long orphanCount, Instant lastReportedAt) {
    }

    /** 删除单条进度（管理端"重置"；设备本地位置不变，下次推送会重建记录）。 */
    @Transactional
    public void delete(Long id) {
        ReadingProgress row = get(id);
        progressRepository.delete(row);
    }

    /**
     * 删除图书时摘链：进度保留为**已结算的孤儿**（R5-c），不会被自动重绑。
     *
     * @return 受影响的进度行数
     */
    @Transactional
    public int detachByBookId(Long bookId) {
        return progressRepository.detachByBookId(bookId, Instant.now());
    }

    /**
     * 记录设备（R6）。
     *
     * <p>设备名<b>原样存</b>、不截断、不做任何规范化——客户端拿 GET 返回的 {@code device}/{@code device_id}
     * 与本地值比较来判断"这是我自己"（调研档 §3 硬约束 11）。SQLite 不强制 VARCHAR 长度，
     * 极端超长值也不会写入失败。</p>
     */
    private void touchDevice(Long syncAccountId, String deviceId, String deviceName, Instant now) {
        SyncDevice device = deviceRepository
                .findBySyncAccountIdAndDeviceId(syncAccountId, deviceId)
                .orElse(null);
        if (device == null) {
            device = new SyncDevice();
            device.setSyncAccountId(syncAccountId);
            device.setDeviceId(deviceId);
            device.setDeviceName(deviceName);
            device.setFirstSeenAt(now);
            device.setLastSeenAt(now);
            device.setReportCount(1L);
        } else {
            device.setDeviceName(deviceName);
            device.setLastSeenAt(now);
            device.setReportCount(device.getReportCount() == null ? 1L : device.getReportCount() + 1);
        }
        deviceRepository.save(device);
    }
}
