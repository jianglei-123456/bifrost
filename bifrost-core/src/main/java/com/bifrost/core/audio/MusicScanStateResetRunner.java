package com.bifrost.core.audio;

import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.LibraryRootRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时重置残留的音乐扫描 SCANNING 状态（进程被杀后状态标记残留，见《音乐管理技术设计》§4.2）。
 *
 * <p>只处理 {@link MediaType#MUSIC} 的音乐目录：图书目录由
 * {@code com.bifrost.core.book.BookScanStateResetRunner} 独立重置（ADR-0004 物理隔开）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MusicScanStateResetRunner implements ApplicationRunner {

    private final LibraryRootRepository libraryRootRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<LibraryRoot> roots = libraryRootRepository.findByMediaTypeOrderByIdAsc(MediaType.MUSIC);
        for (LibraryRoot root : roots) {
            if (root.getScanStatus() == ScanStatus.SCANNING) {
                root.setScanStatus(ScanStatus.IDLE);
                libraryRootRepository.save(root);
                log.info("重置残留音乐扫描状态: root={}", root.getName());
            }
        }
    }
}
