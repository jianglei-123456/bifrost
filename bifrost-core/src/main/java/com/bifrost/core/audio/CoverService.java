package com.bifrost.core.audio;

import com.bifrost.common.util.FileIO;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.repo.AlbumRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 封面服务：内嵌图抽取落盘、目录图（cover.jpg/folder.jpg）、缩略图缓存。
 *
 * <p>来源优先级：内嵌图 > 目录图（《音乐管理功能说明》§6）；
 * 原图存 {@code cover-source/al-<id>.jpg}，缩略图存 {@code cover-cache/al-<id>-<N>.jpg}（Q13 统一 al- ID 体系）。
 * 缓存失效：扫描更新专辑封面时清除对应缓存（T5.8 事件订阅）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverService {

    /** coverSource 标记：内嵌图 */
    public static final String EMBEDDED = "EMBEDDED";

    /** 目录图候选文件名（按优先级） */
    private static final String[] FOLDER_COVER_NAMES = {"cover.jpg", "folder.jpg"};

    private final BifrostProperties properties;
    private final AlbumRepository albumRepository;

    public Path coverSourceDir() {
        return properties.getMedia().getCoverCacheDir().toAbsolutePath().normalize().resolve("cover-source");
    }

    public Path coverCacheDir() {
        return properties.getMedia().getCoverCacheDir().toAbsolutePath().normalize().resolve("cover-cache");
    }

    /**
     * 存储内嵌封面（仅当专辑尚无封面时，取首个，Q 决策）。
     *
     * @return 是否写入（false = 已存在封面或无数据）
     */
    public boolean storeEmbeddedCover(Album album, byte[] coverData) {
        if (coverData == null || coverData.length == 0 || album.getId() == null) {
            return false;
        }
        if (EMBEDDED.equals(album.getCoverSource()) || album.getCoverSource() != null) {
            return false;
        }
        try {
            FileIO.ensureDirs(coverSourceDir());
            Path target = coverSourceDir().resolve("al-" + album.getId() + ".jpg");
            Files.write(target, coverData);
            album.setCoverSource(EMBEDDED);
            albumRepository.save(album);
            return true;
        } catch (IOException e) {
            log.warn("内嵌封面落盘失败: album={}", album.getId(), e);
            return false;
        }
    }

    /**
     * 目录图兜底（仅当专辑尚无封面）：优先 cover.jpg，其次 folder.jpg。
     *
     * @return 是否写入（false = 无目录图或已有封面）
     */
    public boolean storeFolderCover(Album album, Path trackDirectory) {
        if (album.getId() == null || album.getCoverSource() != null || trackDirectory == null) {
            return false;
        }
        for (String name : FOLDER_COVER_NAMES) {
            Path candidate = trackDirectory.resolve(name);
            if (Files.isRegularFile(candidate)) {
                album.setCoverSource(candidate.toAbsolutePath().normalize().toString());
                albumRepository.save(album);
                return true;
            }
        }
        return false;
    }

    /**
     * 解析专辑原始封面（EMBEDDED → cover-source 文件；路径 → 原路径）。
     */
    public Optional<Path> originalCover(Album album) {
        if (album == null || album.getCoverSource() == null) {
            return Optional.empty();
        }
        Path path = EMBEDDED.equals(album.getCoverSource())
                ? coverSourceDir().resolve("al-" + album.getId() + ".jpg")
                : Path.of(album.getCoverSource());
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    /**
     * 读取封面（可按 size 缩放）；无封面返回 empty（调用方按协议返回 70）。
     */
    public Optional<byte[]> coverData(Long albumId, Integer size) {
        Album album = albumRepository.findById(albumId).orElse(null);
        if (album == null) {
            return Optional.empty();
        }
        Optional<Path> original = originalCover(album);
        if (original.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (size == null || size <= 0) {
                return Optional.of(Files.readAllBytes(original.get()));
            }
            return Optional.of(thumbnail(album.getId(), original.get(), size));
        } catch (IOException e) {
            throw new UncheckedIOException("读取封面失败: album=" + albumId, e);
        }
    }

    /** 缩略图：缓存命中直接返回；否则缩放生成并缓存（原图/200px/64px 档位，size 映射最近档）。 */
    private byte[] thumbnail(Long albumId, Path original, int requestedSize) throws IOException {
        int target = requestedSize <= 64 ? 64 : (requestedSize <= 200 ? 200 : requestedSize);
        FileIO.ensureDirs(coverCacheDir());
        Path cache = coverCacheDir().resolve("al-" + albumId + "-" + target + ".jpg");
        if (Files.isRegularFile(cache)) {
            return Files.readAllBytes(cache);
        }
        BufferedImage source = ImageIO.read(original.toFile());
        if (source == null) {
            return Files.readAllBytes(original);
        }
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= 0 || h <= 0) {
            return Files.readAllBytes(original);
        }
        double scale = Math.min((double) target / w, (double) target / h);
        if (scale >= 1) {
            // 原图不大于目标尺寸：直接使用原图
            return Files.readAllBytes(original);
        }
        int nw = Math.max(1, (int) (w * scale));
        int nh = Math.max(1, (int) (h * scale));
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        byte[] data = toJpegBytes(scaled);
        try {
            Files.write(cache, data);
        } catch (IOException e) {
            log.debug("缩略图缓存写入失败（忽略）: {}", cache, e);
        }
        return data;
    }

    private static byte[] toJpegBytes(BufferedImage image) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /** 扫描更新专辑封面时清除缓存（T5.8 事件订阅）。 */
    public void invalidate(Long albumId) {
        if (albumId == null) {
            return;
        }
        try {
            Path dir = coverCacheDir();
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    stream.filter(p -> p.getFileName().toString().startsWith("al-" + albumId + "-"))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                    // 忽略删除失败
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.debug("封面缓存清理失败（忽略）: album={}", albumId, e);
        }
    }
}
