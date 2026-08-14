package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 艺术家仓储。
 */
public interface ArtistRepository extends JpaRepository<Artist, Long> {

    /** 按名称查询（全局唯一，同名合并） */
    Optional<Artist> findByName(String name);

    /** 全部按名称排序（索引分组用） */
    List<Artist> findAllByOrderByNameAsc();

    /** 按名称关键字搜索（搜索用） */
    List<Artist> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);
}
