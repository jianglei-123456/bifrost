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

    /**
     * 按文档指纹查询（M3-sync T1.2）。
     *
     * <p>用 {@code List} 而非 {@code Optional}：同一份文件可能在库里存在多个副本
     * （不同图书目录各一份），调用方取最小 id 作为确定性选择。</p>
     */
    List<Book> findByPartialMd5OrderByIdAsc(String partialMd5);

    /** 文档指纹缺失的数量（启动回填 runner 的快速判空） */
    long countByPartialMd5IsNull();

    /** 文档指纹缺失的书（启动回填 runner 分批处理） */
    Page<Book> findByPartialMd5IsNull(Pageable pageable);

    /** 可见图书（孤儿"建议绑定"的候选集；家庭规模下全量遍历可接受） */
    List<Book> findByIsAvailableTrueOrderByIdAsc();

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