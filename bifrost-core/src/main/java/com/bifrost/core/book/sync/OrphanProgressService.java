package com.bifrost.core.book.sync;

import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.enums.ProgressMatchSource;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.ReadingProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 孤儿进度服务（M3-sync T1.5）。
 *
 * <p>生命周期（R5/C1）：未匹配的新指纹 → 触发一次扫描 → {@link #settlePending(Long) 结算}；
 * 命中则 {@code AUTO} 绑定，未命中则置 {@code scanAttemptedAt} 成为<b>固定孤儿</b>——此后
 * <b>不再自动匹配</b>，只能人工 {@link #bind(Long, Long) 绑定}、{@link #rematch(Long) 重新匹配}
 * 或 {@link #setIgnored(Long, boolean) 忽略}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanProgressService {

    private final ReadingProgressRepository progressRepository;
    private final BookRepository bookRepository;
    private final ProgressBookMatcher matcher;

    /**
     * 结算"待结算"的孤儿（图书扫描完成事件触发）。
     *
     * <p>幂等：{@code scanAttemptedAt != null} 的行不会再被选中。</p>
     *
     * @return 命中并绑定的行数
     */
    @Transactional
    public int settlePending(Long syncAccountId) {
        List<ReadingProgress> pending =
                progressRepository.findBySyncAccountIdAndBookIdIsNullAndScanAttemptedAtIsNull(syncAccountId);
        if (pending.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        int bound = 0;
        for (ReadingProgress row : pending) {
            Book book = matcher.match(row.getDocumentFingerprint()).orElse(null);
            if (book != null) {
                row.setBookId(book.getId());
                row.setMatchSource(ProgressMatchSource.AUTO);
                bound++;
            }
            // 无论命中与否都"结算"：未命中即固定孤儿，不再自动重试
            row.setScanAttemptedAt(now);
            progressRepository.save(row);
        }
        return bound;
    }

    /** 人工绑定到指定图书（孤儿页）。 */
    @Transactional
    public ReadingProgress bind(Long progressId, Long bookId) {
        ReadingProgress row = get(progressId);
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> BizException.notFound("图书不存在: " + bookId));
        if (!Boolean.TRUE.equals(book.getIsAvailable())) {
            throw BizException.paramError("图书文件缺失，无法绑定: " + book.getTitle());
        }
        row.setBookId(book.getId());
        row.setMatchSource(ProgressMatchSource.MANUAL);
        row.setIgnored(false);
        if (row.getScanAttemptedAt() == null) {
            row.setScanAttemptedAt(Instant.now());
        }
        return progressRepository.save(row);
    }

    /**
     * 人工"重新匹配"一次（用于"我后来把书放进库了"）。
     *
     * <p>未命中是<b>正常结果</b>而非错误——返回 {@code matched=false}，由管理端提示"仍未匹配到图书"。</p>
     */
    @Transactional
    public RematchResult rematch(Long progressId) {
        ReadingProgress row = get(progressId);
        Book book = matcher.match(row.getDocumentFingerprint()).orElse(null);
        if (book == null) {
            if (row.getScanAttemptedAt() == null) {
                row.setScanAttemptedAt(Instant.now());
                progressRepository.save(row);
            }
            return new RematchResult(false, null);
        }
        row.setBookId(book.getId());
        row.setMatchSource(ProgressMatchSource.AUTO);
        row.setIgnored(false);
        if (row.getScanAttemptedAt() == null) {
            row.setScanAttemptedAt(Instant.now());
        }
        return new RematchResult(true, progressRepository.save(row));
    }

    /** 忽略 / 取消忽略。 */
    @Transactional
    public ReadingProgress setIgnored(Long progressId, boolean ignored) {
        ReadingProgress row = get(progressId);
        row.setIgnored(ignored);
        return progressRepository.save(row);
    }

    private ReadingProgress get(Long progressId) {
        return progressRepository.findById(progressId)
                .orElseThrow(() -> BizException.notFound("进度记录不存在: " + progressId));
    }

    /** 重新匹配结果（{@code matched=false} 时 {@code progress} 为 null）。 */
    public record RematchResult(boolean matched, ReadingProgress progress) {
    }
}
