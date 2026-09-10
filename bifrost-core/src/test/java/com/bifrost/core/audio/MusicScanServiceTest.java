package com.bifrost.core.audio;

import com.bifrost.common.exception.BizException;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.BookmarkRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.PlayQueueEntryRepository;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.TrackRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link MusicScanService} 单测：媒体边界与扫描状态机不变量。
 *
 * <p>覆盖两条管理端（音乐库页）直接依赖的行为：非 MUSIC 目录被拒绝；扫描主体抛异常后
 * 不得把目录留在 {@code SCANNING}（否则管理端扫描按钮会被永久禁用直到进程重启）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MusicScanServiceTest {

    @Mock private LibraryRootRepository libraryRootRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private PlaylistEntryRepository playlistEntryRepository;
    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private PlayQueueEntryRepository playQueueEntryRepository;
    @Mock private AggregationService aggregationService;
    @Mock private CoverService coverService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    @TempDir Path tempDir;

    private MusicScanService service;

    private void newService() {
        BifrostProperties p = new BifrostProperties();
        p.getMedia().setCoverCacheDir(tempDir);
        p.getScan().setBatchSize(2);
        service = new MusicScanService(libraryRootRepository, trackRepository, playlistEntryRepository,
                bookmarkRepository, playQueueEntryRepository, aggregationService, coverService,
                eventPublisher, transactionManager, p);
    }

    private static LibraryRoot root(Path dir, long id, MediaType type) {
        LibraryRoot r = new LibraryRoot();
        r.setId(id);
        r.setName("测试音乐目录");
        r.setPath(dir.toString());
        r.setEnabled(true);
        r.setMediaType(type);
        r.setScanStatus(ScanStatus.IDLE);
        return r;
    }

    @Test
    void scanRootRejectsNonMusicRoot() {
        newService();
        LibraryRoot bookRoot = root(tempDir, 12L, MediaType.BOOK);
        when(libraryRootRepository.findById(12L)).thenReturn(Optional.of(bookRoot));

        BizException ex = assertThrows(BizException.class, () -> service.scanRoot(12L));

        assertTrue(ex.getMessage().contains("非音乐类型"));
        assertEquals(ScanStatus.IDLE, bookRoot.getScanStatus());
    }

    @Test
    void scanRootResetsScanningWhenScanBodyThrows() {
        newService();
        LibraryRoot r = root(tempDir, 11L, MediaType.MUSIC);
        when(libraryRootRepository.findById(11L)).thenReturn(Optional.of(r));
        when(libraryRootRepository.save(any(LibraryRoot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(trackRepository.findByLibraryRootId(11L)).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> service.scanRoot(11L));

        // 状态机不变量：异常后必须回到 IDLE（/scan/status 不再报 scanning=true）
        assertEquals(ScanStatus.IDLE, r.getScanStatus());
    }
}
