package com.bifrost.core.book;

import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.LibraryRootRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时重置残留的图书扫描 SCANNING 状态（进程被杀后状态标记残留，与 {@code MusicScanStateResetRunner} 同模式）。
 *
 * <p>仅重置 {@link MediaType#BOOK} 图书目录；音乐目录由 {@code MusicScanStateResetRunner} 独立处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookScanStateResetRunner implements ApplicationRunner {

    private final LibraryRootRepository libraryRootRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (LibraryRoot root : libraryRootRepository.findAll()) {
            if (root.getMediaType() == MediaType.BOOK
                    && root.getScanStatus() == ScanStatus.SCANNING) {
                root.setScanStatus(ScanStatus.IDLE);
                libraryRootRepository.save(root);
                log.info("重置残留图书扫描状态: root={}", root.getName());
            }
        }
    }
}