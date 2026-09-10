package com.bifrost.domain.repo;

import com.bifrost.domain.entity.ReadingProgress;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 阅读进度仓储（M3-sync T1.1）。
 */
public interface ReadingProgressRepository extends JpaRepository<ReadingProgress, Long> {

    /** 协议读路径：账号 + 文档指纹（唯一） */
    Optional<ReadingProgress> findBySyncAccountIdAndDocumentFingerprint(Long syncAccountId, String documentFingerprint);

    /** 某本书的全部进度（删书摘链、概览统计用） */
    List<ReadingProgress> findByBookId(Long bookId);

    /** 待结算的孤儿：本次未匹配且尚未结算（自动扫描完成后据此回填） */
    List<ReadingProgress> findBySyncAccountIdAndBookIdIsNullAndScanAttemptedAtIsNull(Long syncAccountId);

    /** 孤儿（含已结算与未结算） */
    Page<ReadingProgress> findBySyncAccountIdAndBookIdIsNull(Long syncAccountId, Pageable pageable);

    /** 全部进度（分页） */
    Page<ReadingProgress> findBySyncAccountId(Long syncAccountId, Pageable pageable);

    long countBySyncAccountId(Long syncAccountId);

    long countBySyncAccountIdAndBookIdIsNull(Long syncAccountId);

    long countBySyncAccountIdAndBookIdIsNotNull(Long syncAccountId);

    /** 最近一次上报时间（概览卡片；无数据时返回 null） */
    @Query("select max(p.reportedAt) from ReadingProgress p")
    Instant findLastReportedAt();

    /**
     * 删除图书时"摘链"：进度保留为孤儿的已结算形态（R5-c）。
     *
     * <p>置 {@code scanAttemptedAt} 是为了让它<b>不会被自动重绑</b>——与"未匹配新指纹只自动匹配
     * 一次"的规则一致（C1）。</p>
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ReadingProgress p set p.bookId = null, p.matchSource = null, p.ignored = false, "
            + "p.scanAttemptedAt = :now where p.bookId = :bookId")
    int detachByBookId(@Param("bookId") Long bookId, @Param("now") Instant now);
}
