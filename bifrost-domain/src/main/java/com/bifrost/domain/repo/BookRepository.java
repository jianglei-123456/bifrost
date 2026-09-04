package com.bifrost.domain.repo;

import com.bifrost.domain.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 图书仓储。
 *
 * <p>扫描用 {@link #findByLibraryRootId(Long)} 构造路径→Book 索引；
 * 管理 REST 列表走 {@link JpaSpecificationExecutor#findAll} 动态过滤；
 * 搜索走 {@link #searchByKeyword} 多字段 LIKE（Q19）。</p>
 */
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    /** 按库根查询（扫描用） */
    List<Book> findByLibraryRootId(Long libraryRootId);

    /** 按文件绝对路径查询（唯一） */
    Optional<Book> findByFilePath(String filePath);

    /** 多字段搜索（仅可见；title/authors/series/identifier/publisher/subject 任一 LIKE q，Q19） */
    @Query("select b from Book b where b.isAvailable = true "
            + "and (lower(b.title) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(b.authors, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(b.series, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(b.identifier, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(b.publisher, '')) like lower(concat('%', :q, '%')) "
            + "or lower(coalesce(b.subject, '')) like lower(concat('%', :q, '%'))) "
            + "order by b.createdAt desc")
    Page<Book> searchByKeyword(@Param("q") String keyword, Pageable pageable);
}