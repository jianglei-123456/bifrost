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

    /** 孤儿（未忽略）——孤儿页签默认视图 */
    Page<ReadingProgress> findBySyncAccountIdAndBookIdIsNullAndIgnoredFalse(Long syncAccountId, Pageable pageable);

    /**
     * 管理端列表（M3-sync T3.3）：按书标题 / 图书目录 / 设备过滤，可选"仅看孤儿"。
     *
     * <p>用实体 join（{@code left join Book b on p.bookId = b.id}）而不是关联映射——本项目
     * 的实体之间一律是扁平 {@code Long} 外键（无 JPA 关联）。</p>
     */
    @Query("select p from ReadingProgress p left join Book b on p.bookId = b.id "
            + "where p.syncAccountId = :accountId "
            + "and (:onlyOrphans = false or p.bookId is null) "
            + "and (:title is null or lower(b.title) like lower(concat('%', :title, '%'))) "
            + "and (:libraryRootId is null or b.libraryRootId = :libraryRootId) "
            + "and (:device is null or lower(p.device) like lower(concat('%', :device, '%')))")
    Page<ReadingProgress> search(@Param("accountId") Long accountId,
                                 @Param("onlyOrphans") boolean onlyOrphans,
                                 @Param("title") String title,
                                 @Param("libraryRootId") Long libraryRootId,
                                 @Param("device") String device,
                                 Pageable pageable);

    /** 全部进度（分页） */
    Page<ReadingProgress> findBySyncAccountId(Long syncAccountId, Pageable pageable);

    long countBySyncAccountId(Long syncAccountId);

    long countBySyncAccountIdAndBookIdIsNull(Long syncAccountId);

    long countBySyncAccountIdAndBookIdIsNotNull(Long syncAccountId);

    /** 最近一次上报时间（概览卡片；无数据时返回 null） */
    @Query("select max(p.reportedAt) from ReadingProgress p where p.syncAccountId = :accountId")
    Instant findLastReportedAt(@Param("accountId") Long accountId);

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
