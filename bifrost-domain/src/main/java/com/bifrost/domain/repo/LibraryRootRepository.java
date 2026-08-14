package com.bifrost.domain.repo;

import com.bifrost.domain.entity.LibraryRoot;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
