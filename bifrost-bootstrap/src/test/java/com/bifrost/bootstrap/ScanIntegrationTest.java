package com.bifrost.bootstrap;

import com.bifrost.common.exception.BizException;
import com.bifrost.core.audio.UnknownArtist;
import com.bifrost.core.audio.service.AnnotationService;
import com.bifrost.core.audio.service.LibraryQueryService;
import com.bifrost.core.audio.service.PlaylistService;
import com.bifrost.core.audio.service.ScrobbleService;
import com.bifrost.core.audio.ScanService;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.Artist;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.entity.Playlist;
import com.bifrost.domain.entity.PlaylistEntry;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.entity.User;
import com.bifrost.domain.enums.UserRole;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
import com.bifrost.domain.repo.PlaylistEntryRepository;
import com.bifrost.domain.repo.PlaylistRepository;
import com.bifrost.domain.repo.TrackRepository;
import com.bifrost.domain.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 02 扫描集成测试：建库、聚合、幂等、变更/缺失检测、未知艺术家、标注/统计/歌单。
 */
@SpringBootTest(properties = {"bifrost.db.path=target/test-data/scan-test.db",
        "BIFROST_AUTH_INITIAL_PASSWORD=testpass"})
class ScanIntegrationTest {

    @Autowired
    private ScanService scanService;
    @Autowired
    private LibraryRootRepository libraryRootRepository;
    @Autowired
    private TrackRepository trackRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private AlbumRepository albumRepository;
    @Autowired
    private LibraryQueryService queryService;
    @Autowired
    private AnnotationService annotationService;
    @Autowired
    private ScrobbleService scrobbleService;
    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private PlaylistRepository playlistRepository;
    @Autowired
    private PlaylistEntryRepository playlistEntryRepository;
    @Autowired
    private UserRepository userRepository;

    @TempDir
    Path musicDir;

    @BeforeEach
    void cleanDatabase() {
        playlistEntryRepository.deleteAll();
        playlistRepository.deleteAll();
        trackRepository.deleteAll();
        albumRepository.deleteAll();
        artistRepository.deleteAll();
        libraryRootRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void scanCreatesLibraryAndAggregates() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        ScanStats stats = scanService.scanRoot(root.getId());

        assertEquals(4, stats.added());
        List<Track> tracks = trackRepository.findByLibraryRootId(root.getId());
        assertEquals(4, tracks.size());

        // 艺术家聚合（周杰伦 → Z 组）
        Artist jay = artistRepository.findByName("周杰伦").orElseThrow();
        assertEquals("Z", jay.getIndexLetter());
        // 专辑聚合（叶惠美 2 曲）
        Album album = albumRepository.findByAlbumArtistNameIgnoreCaseAndTitleIgnoreCase("周杰伦", "叶惠美").orElseThrow();
        assertEquals(2, trackRepository.findByAlbumId(album.getId()).size());
        assertEquals(jay.getId(), album.getArtistId());
        assertTrue(album.getDuration() > 0);
        // 曲目排序 discNo → trackNo
        List<Track> albumTracks = queryService.albumTracksSorted(album.getId());
        assertEquals(1, albumTracks.get(0).getTrackNo());
        assertEquals(2, albumTracks.get(1).getTrackNo());

        // 未知艺术家：无署名曲目 artistId 为空
        Track unknown = trackRepository.findAll().stream().filter(t -> t.getArtistName() == null).findFirst().orElseThrow();
        assertNull(unknown.getArtistId());

        // 索引分组含虚拟"未知艺术家"（# 组）
        List<LibraryQueryService.ArtistIndexGroup> index = queryService.artistIndex(null);
        assertTrue(index.stream().anyMatch(g -> g.letter().equals("Z")
                && g.artists().stream().anyMatch(a -> a.name().equals("周杰伦"))));
        assertTrue(index.stream().anyMatch(g -> g.artists().stream()
                .anyMatch(a -> a.unknown() && a.name().equals(UnknownArtist.NAME))));

        // 库根状态
        LibraryRoot saved = libraryRootRepository.findById(root.getId()).orElseThrow();
        assertNotNull(saved.getLastScanAt());
        assertNotNull(saved.getLastScanStats());
    }

    @Test
    void rescanIsIdempotent() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        scanService.scanRoot(root.getId());
        ScanStats second = scanService.scanRoot(root.getId());
        assertEquals(0, second.added());
        assertEquals(0, second.updated());
        assertEquals(0, second.missing());
        assertEquals(4, trackRepository.findByLibraryRootId(root.getId()).size());
    }

    @Test
    void fullRescanReParsesUnchangedFiles() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        assertEquals(4, scanService.scanRoot(root.getId()).added());
        // 增量幂等：指纹一致 → 全部跳过
        assertEquals(0, scanService.scanRoot(root.getId()).updated());
        // 强制全量重解析：指纹一致也重读标签（回填歌词等新字段的场景）
        ScanStats forced = scanService.scanRoot(root.getId(), true);
        assertEquals(0, forced.added());
        assertEquals(4, forced.updated()); // 每首曲目都被重解析（updated 计数=重解析）
        assertEquals(0, forced.missing());
        assertEquals(4, trackRepository.findByLibraryRootId(root.getId()).size());
        // 强制扫描不破坏指纹，随后增量扫描仍幂等
        assertEquals(0, scanService.scanRoot(root.getId()).updated());
    }

    @Test
    void fullRescanPurgesDeletedFiles() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        scanService.scanRoot(root.getId());
        Track gone = trackRepository.findAll().stream()
                .filter(t -> t.getFilePath().endsWith("东风破.mp3")).findFirst().orElseThrow();
        Files.delete(musicDir.resolve("周杰伦/叶惠美/02 - 东风破.mp3"));
        // 歌单引用全部曲目
        Long playlistId = playlistService.create("引用歌单", null, 1L).getId();
        for (Track t : trackRepository.findAll()) {
            playlistService.addEntry(playlistId, t.getId());
        }
        assertEquals(4, playlistEntryRepository.findByPlaylistId(playlistId).size());

        // 增量扫描：缺失→标记隐藏，记录与歌单条目保留
        ScanStats inc = scanService.scanRoot(root.getId());
        assertEquals(1, inc.missing());
        assertTrue(trackRepository.findById(gone.getId()).isPresent());
        assertEquals(4, playlistEntryRepository.findByPlaylistId(playlistId).size());

        // 全量重扫：已删文件彻底移除（含隐藏记录），并清理歌单条目/书签/播放队列引用
        ScanStats full = scanService.scanRoot(root.getId(), true);
        assertEquals(1, full.missing());
        assertTrue(trackRepository.findById(gone.getId()).isEmpty());
        assertEquals(3, trackRepository.findByLibraryRootId(root.getId()).size());
        assertEquals(3, playlistEntryRepository.findByPlaylistId(playlistId).size());
        // 全量重扫后无缺失残留：再全量重扫 missing=0
        ScanStats full2 = scanService.scanRoot(root.getId(), true);
        assertEquals(0, full2.missing());
    }

    @Test
    void deletedFileRestoredSameMtimeIsResurrected() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        scanService.scanRoot(root.getId());
        Path f = musicDir.resolve("周杰伦/叶惠美/02 - 东风破.mp3");
        Track gone = trackRepository.findAll().stream()
                .filter(t -> t.getFilePath().endsWith("东风破.mp3")).findFirst().orElseThrow();
        long originalMtime = Files.getLastModifiedTime(f).toMillis();
        byte[] bytes = Files.readAllBytes(f);
        Files.delete(f);
        ScanStats hidden = scanService.scanRoot(root.getId());
        assertEquals(1, hidden.missing());
        assertFalse(trackRepository.findById(gone.getId()).orElseThrow().getIsAvailable());
        // 原样拷回并还原 mtime → 指纹与"隐藏记录"完全一致
        Files.write(f, bytes);
        Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(originalMtime));
        ScanStats back = scanService.scanRoot(root.getId());
        assertEquals(0, back.added()); // 不是新增，而是复活原记录
        assertEquals(0, back.missing());
        assertTrue(trackRepository.findById(gone.getId()).orElseThrow().getIsAvailable());
        assertEquals(4, trackRepository.findByLibraryRootId(root.getId()).size());
    }

    @Test
    void fileChangeAndDeleteDetected() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        scanService.scanRoot(root.getId());

        // 变更：向"以父之名.mp3"追加字节（大小+mtime 变化 → 指纹变化）
        Path changed = musicDir.resolve("周杰伦/叶惠美/01 - 以父之名.mp3");
        Files.write(changed, Files.readAllBytes(changed), java.nio.file.StandardOpenOption.APPEND);
        // 删除"东风破.mp3"
        Files.delete(musicDir.resolve("周杰伦/叶惠美/02 - 东风破.mp3"));

        ScanStats stats = scanService.scanRoot(root.getId());
        assertEquals(1, stats.updated());
        assertEquals(1, stats.missing());

        Track missing = trackRepository.findAll().stream()
                .filter(t -> t.getFilePath().endsWith("东风破.mp3")).findFirst().orElseThrow();
        assertFalse(missing.getIsAvailable());
        // 歌单引用保留（缺失不删除记录）
        assertNotNull(missing.getId());
    }

    @Test
    void concurrentScanRejected() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        // 让另一线程占用全局扫描锁模拟并发扫描 → 本线程触发扫描抛 1100（ReentrantLock 可重入，须跨线程持锁）
        Object target = org.springframework.test.util.AopTestUtils.getTargetObject(scanService);
        java.lang.reflect.Field lockField = ScanService.class.getDeclaredField("globalLock");
        lockField.setAccessible(true);
        java.util.concurrent.locks.ReentrantLock lock =
                (java.util.concurrent.locks.ReentrantLock) lockField.get(target);
        java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            held.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        holder.start();
        assertTrue(held.await(5, java.util.concurrent.TimeUnit.SECONDS), "持锁线程未就绪");
        BizException ex = assertThrows(BizException.class, () -> scanService.scanRoot(root.getId()));
        assertEquals(1100, ex.getCode());
        release.countDown();
        holder.join(5000); // 等待持锁线程释放锁，避免竞态
        // 解锁后恢复正常扫描
        ScanStats stats = scanService.scanRoot(root.getId());
        assertEquals(4, stats.added());
    }

    @Test
    void annotationScrobbleAndPlaylist() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        scanService.scanRoot(root.getId());

        User admin = new User();
        admin.setUsername("admin");
        admin.setEncryptedPassword("enc");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        Album album = albumRepository.findByAlbumArtistNameIgnoreCaseAndTitleIgnoreCase("周杰伦", "叶惠美").orElseThrow();
        Artist artist = artistRepository.findByName("周杰伦").orElseThrow();
        Track track = trackRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("以父之名")).findFirst().orElseThrow();

        // 收藏/评分（三态）
        annotationService.star(AnnotationService.TYPE_ALBUM, album.getId());
        annotationService.star(AnnotationService.TYPE_ARTIST, artist.getId());
        assertNotNull(albumRepository.findById(album.getId()).orElseThrow().getStarredAt());
        assertNotNull(artistRepository.findById(artist.getId()).orElseThrow().getStarredAt());
        annotationService.rate(AnnotationService.TYPE_TRACK, track.getId(), 5);
        assertEquals(5, trackRepository.findById(track.getId()).orElseThrow().getRating());

        // 播放统计：scrobble submission=true 落库
        scrobbleService.scrobble(track.getId(), null, true, "admin", "test");
        assertEquals(1, trackRepository.findById(track.getId()).orElseThrow().getPlayCount());

        // 歌单：创建/追加/删除条目
        Playlist playlist = playlistService.create("我的歌单", "备注", admin.getId());
        PlaylistEntry e1 = playlistService.addEntry(playlist.getId(), track.getId());
        Track track2 = trackRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("东风破")).findFirst().orElseThrow();
        playlistService.addEntry(playlist.getId(), track2.getId());
        PlaylistService.PlaylistWithEntries view = playlistService.get(playlist.getId());
        assertEquals(2, view.entries().size());
        playlistService.removeEntry(playlist.getId(), e1.getId());
        view = playlistService.get(playlist.getId());
        assertEquals(1, view.entries().size());
        assertEquals(1, view.entries().get(0).getPosition()); // 重排
    }

    @Test
    void scanDisabledRootRejected() throws Exception {
        createSampleLibrary();
        LibraryRoot root = createRoot();
        root.setEnabled(false);
        libraryRootRepository.save(root);
        BizException ex = assertThrows(BizException.class, () -> scanService.scanRoot(root.getId()));
        assertEquals(1000, ex.getCode());
    }

    private LibraryRoot createRoot() {
        LibraryRoot root = new LibraryRoot();
        root.setName("测试库");
        root.setPath(musicDir.toAbsolutePath().normalize().toString());
        root.setEnabled(true);
        return libraryRootRepository.save(root);
    }

    private void createSampleLibrary() throws Exception {
        // 周杰伦/叶惠美（2 曲）
        MiniMediaFactory.writeMp3(musicDir.resolve("周杰伦/叶惠美"), "01 - 以父之名.mp3",
                "以父之名", "周杰伦", "周杰伦", "叶惠美", 1, 2003);
        MiniMediaFactory.writeMp3(musicDir.resolve("周杰伦/叶惠美"), "02 - 东风破.mp3",
                "东风破", "周杰伦", "周杰伦", "叶惠美", 2, 2003);
        // Beatles/Let It Be（flac）
        MiniMediaFactory.writeFlac(musicDir.resolve("Beatles/Let It Be"), "01 - Let It Be.flac",
                "Let It Be", "The Beatles", "The Beatles", "Let It Be", 1, 1970);
        // 无署名曲目（未知艺术家）
        MiniMediaFactory.writeMp3(musicDir.resolve("solo"), "01 - 无题.mp3",
                "无题", null, null, null, 1, 2000);
    }
}
