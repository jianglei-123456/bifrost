package com.bifrost.core.book;

import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.repo.LibraryRootRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 启动时回填 {@code library_root.mediaType} 列（M2-book 增列；历史行通过该 runner 显式置 MUSIC）。
 *
 * <p>{@code ddl-auto: update} 在添加 {@code NOT NULL} 列到含数据的旧表时不会自动填默认值，
 * 这里在 H2/SQLite 上做一次显式 UPDATE。所有现有库根归 MUSIC（默认）。</p>
 *
 * <p>顺序：在 {@link com.bifrost.core.audio.ScanStateResetRunner} 之前完成回填，
 * 以免 SCANNING 检测到 mediaType 异常。</p>
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class LibraryRootMediaTypeBackfillRunner implements ApplicationRunner {

    private final LibraryRootRepository libraryRootRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 通过 JPQL 显式回填"mediaType 为 null"的行（Hibernate/JPA 缓存里 default 值未刷盘）
        // 注意：枚举字段在 JPQL 里需要包装为字符串比较（@Enumerated(STRING)）
        int updated = libraryRootRepository.backfillNullMediaType(MediaType.MUSIC);
        if (updated > 0) {
            log.info("回填 library_root.mediaType=MUSIC: {} 行", updated);
        }
        // 防御：仍存在 null 的（极端情况：超长 enum 等），强写
        List<LibraryRoot> all = libraryRootRepository.findAll();
        int defensive = 0;
        for (LibraryRoot r : all) {
            if (r.getMediaType() == null) {
                r.setMediaType(MediaType.MUSIC);
                libraryRootRepository.save(r);
                defensive++;
            }
        }
        if (defensive > 0) {
            log.info("二次回填 library_root.mediaType: {} 行", defensive);
        }
    }
}