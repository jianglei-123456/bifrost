package com.bifrost.core.audio;

import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.FileIO;
import com.bifrost.common.util.Fingerprint;
import com.bifrost.core.audio.model.TagResult;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.event.ScanCompletedEvent;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.Artist;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.BookmarkRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.PlayQueueEntryRepository;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.TrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 扫描引擎（全局单扫描互斥、指纹增量、批事务）。
 *
 * <p>见《音乐管理技术设计》§4：遍历库根 → 指纹比对 → 解析入库 → 聚合刷新 → 发布 ScanCompletedEvent。
 * 同一时刻全局只允许一个扫描（手动/定时/Subsonic 触发共用，后到者抛"扫描进行中"）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final LibraryRootRepository libraryRootRepository;
    private final TrackRepository trackRepository;
    private final PlaylistEntryRepository playlistEntryRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PlayQueueEntryRepository playQueueEntryRepository;
    private final AggregationService aggregationService;
    private final CoverService coverService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final BifrostProperties properties;

    private final ReentrantLock globalLock = new ReentrantLock();

    /** 扫描全部启用库根（串行，增量：指纹一致跳过）。 */
    public ScanStats scanAll() {
        return scanAll(false);
    }

    /**
     * 扫描全部启用库根（串行）。
     *
     * @param force true=强制全量重解析（忽略指纹跳过，重读全部标签——用于回填歌词等新字段/修正元数据）；
     *              false=增量（仅解析新增/变更文件）
     */
    public ScanStats scanAll(boolean force) {
        List<LibraryRoot> roots = libraryRootRepository.findAllByOrderByIdAsc();
        int added = 0, updated = 0, missing = 0, error = 0;
        for (LibraryRoot root : roots) {
            if (!Boolean.TRUE.equals(root.getEnabled())) {
                continue;
            }
            ScanStats s = scanRoot(root.getId(), force);
            added += s.added();
            updated += s.updated();
            missing += s.missing();
            error += s.error();
        }
        return new ScanStats(added, updated, missing, error);
    }

    /** 扫描单个库根（增量）；扫描进行中抛 {@link BizException}（1100）。 */
    public ScanStats scanRoot(Long rootId) {
        return scanRoot(rootId, false);
    }

    /** 扫描单个库根；force=true 全量重解析；扫描进行中抛 {@link BizException}（1100）。 */
    public ScanStats scanRoot(Long rootId, boolean force) {
        if (!globalLock.tryLock()) {
            throw BizException.scanInProgress("扫描进行中");
        }
        try {
            LibraryRoot root = libraryRootRepository.findById(rootId)
                    .orElseThrow(() -> BizException.notFound("库根不存在: " + rootId));
            if (!Boolean.TRUE.equals(root.getEnabled())) {
                throw BizException.paramError("库根已禁用: " + root.getName());
            }
            root.setScanStatus(ScanStatus.SCANNING);
            libraryRootRepository.save(root);
            ScanStats stats = scanLibraryRoot(root, force);
            root.setScanStatus(ScanStatus.IDLE);
            root.setLastScanAt(Instant.now());
            root.setLastScanStats(toJson(stats));
            libraryRootRepository.save(root);
            eventPublisher.publishEvent(new ScanCompletedEvent(root.getId(), stats));
            return stats;
        } finally {
            globalLock.unlock();
        }
    }

    /** 单库根扫描主体。 */
    private ScanStats scanLibraryRoot(LibraryRoot root, boolean force) {
        Path dir = Path.of(root.getPath());
        if (!Files.isDirectory(dir)) {
            log.warn("库根目录不存在，跳过扫描: {}", root.getPath());
            return new ScanStats(0, 0, 0, 1);
        }
        Map<String, Track> dbIndex = trackRepository.findByLibraryRootId(root.getId()).stream()
                .collect(Collectors.toMap(Track::getFilePath, Function.identity()));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int batchSize = Math.max(1, properties.getScan().getBatchSize());
        Counters counters = new Counters();
        Set<Long> affectedAlbums = new HashSet<>();
        List<ParsedTrack> pending = new ArrayList<>();

        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> AudioTagParser.SUPPORTED_EXTENSIONS.contains(FileIO.extension(p)))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("遍历库根失败: " + root.getPath(), e);
        }

        for (Path file : files) {
            String abs = file.toAbsolutePath().normalize().toString();
            Track existing = dbIndex.remove(abs);
            String fingerprint = Fingerprint.of(file);
            if (!force && existing != null && Boolean.TRUE.equals(existing.getIsAvailable())
                    && fingerprint.equals(existing.getFingerprint())) {
                continue; // 增量且可用且指纹一致 → 跳过；隐藏记录或 force 全量重扫需重解析（复活/回填）
            }
            TagResult tag = AudioTagParser.parse(file);
            if (tag.parseError()) {
                counters.error++;
            }
            ParsedTrack parsed = new ParsedTrack(existing != null ? existing : new Track(), tag, file, abs, fingerprint,
                    root.getId());
            if (existing == null) {
                counters.added++;
            } else {
                counters.updated++;
            }
            pending.add(parsed);
            if (pending.size() >= batchSize) {
                flush(tx, pending, affectedAlbums);
            }
        }
        flush(tx, pending, affectedAlbums);

        // 剩余记录 = 文件已消失：增量 → 标记缺失（保留记录与歌单引用）；全量重扫 → 彻底移除
        List<Track> remaining = new ArrayList<>(dbIndex.values());
        tx.executeWithoutResult(status -> {
            for (Track t : remaining) {
                if (force) {
                    purgeTrack(t, affectedAlbums, counters);
                } else {
                    if (Boolean.TRUE.equals(t.getIsAvailable())) {
                        t.setIsAvailable(false);
                        trackRepository.save(t);
                        counters.missing++;
                    }
                    if (t.getAlbumId() != null) {
                        affectedAlbums.add(t.getAlbumId());
                    }
                }
            }
        });

        // 聚合刷新（仅本次涉及专辑）
        aggregationService.refreshAlbumAggregates(affectedAlbums);

        log.info("扫描完成: root={} added={} updated={} missing={} error={}",
                root.getName(), counters.added, counters.updated, counters.missing, counters.error);
        return new ScanStats(counters.added, counters.updated, counters.missing, counters.error);
    }

    /**
     * 全量重扫下彻底移除已消失曲目：连带清理歌单条目/书签/播放队列条目对该曲目的引用。
     * 不可逆——仅当确认文件确实删除后再用 fullScan（外置盘临时离线时请勿触发）。
     */
    private void purgeTrack(Track t, Set<Long> affectedAlbums, Counters counters) {
        if (t.getAlbumId() != null) {
            affectedAlbums.add(t.getAlbumId());
        }
        playlistEntryRepository.deleteByTrackId(t.getId());
        bookmarkRepository.deleteByTrackId(t.getId());
        playQueueEntryRepository.deleteByTrackId(t.getId());
        trackRepository.deleteById(t.getId()); // deleteById 内部自建事务加载托管实体再删（实体来自上一查询上下文，游离态不可 em.remove）
        counters.missing++;
    }

    /** 批事务提交（200 文件/批，含聚合解析与封面处理）。 */
    private void flush(TransactionTemplate tx, List<ParsedTrack> pending, Set<Long> affectedAlbums) {
        if (pending.isEmpty()) {
            return;
        }
        List<ParsedTrack> batch = List.copyOf(pending);
        pending.clear();
        tx.executeWithoutResult(status -> {
            for (ParsedTrack p : batch) {
                Track t = p.track();
                TagResult tag = p.tag();
                applyTagFields(t, tag, p.file(), p.absPath(), p.fingerprint(), p.libraryRootId());
                Artist artist = aggregationService.findOrCreateArtist(tag.artistName());
                t.setArtistId(artist == null ? null : artist.getId());
                Album album = aggregationService.findOrCreateAlbum(tag.albumArtistName(), tag.albumTitle(),
                        artist == null ? null : artist.getId());
                t.setAlbumId(album.getId());
                affectedAlbums.add(album.getId());
                trackRepository.save(t);
                // 封面：内嵌图 > 目录图（仅首次）
                if (!coverService.storeEmbeddedCover(album, tag.embeddedCover())) {
                    coverService.storeFolderCover(album, p.file().getParent());
                }
            }
        });
    }

    private void applyTagFields(Track t, TagResult tag, Path file, String absPath, String fingerprint, Long rootId) {
        t.setTitle(tag.title());
        t.setTrackNo(tag.trackNo());
        t.setDiscNo(tag.discNo());
        t.setArtistName(tag.artistName());
        t.setAlbumArtistName(tag.albumArtistName());
        t.setGenre(tag.genre());
        t.setYear(tag.year());
        t.setDuration(tag.durationSeconds() == null ? 0 : tag.durationSeconds());
        t.setBitrate(tag.bitrate());
        t.setSampleRate(tag.sampleRate());
        t.setFormat(FileIO.extension(file));
        t.setFilePath(absPath);
        t.setFileSize(FileIO.size(file));
        t.setFileLastModified(FileIO.lastModifiedMillis(file));
        t.setFingerprint(fingerprint);
        t.setLyrics(tag.lyrics());
        t.setLibraryRootId(rootId);
        t.setIsAvailable(true);
    }

    private static String toJson(ScanStats stats) {
        return "{\"added\":" + stats.added() + ",\"updated\":" + stats.updated()
                + ",\"missing\":" + stats.missing() + ",\"error\":" + stats.error() + "}";
    }

    /** 待入库曲目中间态（含标签与文件信息）。 */
    private record ParsedTrack(Track track, TagResult tag, Path file, String absPath, String fingerprint,
                               Long libraryRootId) {
    }

    private static final class Counters {
        int added;
        int updated;
        int missing;
        int error;
    }
}
