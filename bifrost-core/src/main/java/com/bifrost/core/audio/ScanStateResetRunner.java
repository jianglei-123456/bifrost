package com.bifrost.core.audio;

import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.LibraryRootRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时重置残留的 SCANNING 状态（进程被杀后状态标记残留，见《音乐管理技术设计》§4.2）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanStateResetRunner implements ApplicationRunner {

    private final LibraryRootRepository libraryRootRepository;

    @Override
    public void run(ApplicationArguments args) {
        for (LibraryRoot root : libraryRootRepository.findAll()) {
            if (root.getScanStatus() == ScanStatus.SCANNING) {
                root.setScanStatus(ScanStatus.IDLE);
                libraryRootRepository.save(root);
                log.info("重置残留扫描状态: root={}", root.getName());
            }
        }
    }
}
