package com.bifrost.domain.repo;

import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 库根仓储。
 */
public interface LibraryRootRepository extends JpaRepository<LibraryRoot, Long> {

    /** 按路径查询（唯一） */
    Optional<LibraryRoot> findByPath(String path);

    /** 全部库根按 ID 排序 */
    List<LibraryRoot> findAllByOrderByIdAsc();

    /** 按媒体类型 + 启用状态过滤 */
    List<LibraryRoot> findByMediaTypeAndEnabledTrueOrderByIdAsc(MediaType mediaType);

    /**
     * 启动时回填：将 {@code mediaType} 为 null 的行（@Enumerated(STRING)）置为传入值。
     * 仅用于 ddl-auto=update 添加新列后的历史数据兜底。
     */
    @Modifying
    @Query("update LibraryRoot r set r.mediaType = :type where r.mediaType is null")
    int backfillNullMediaType(@Param("type") MediaType type);
}
