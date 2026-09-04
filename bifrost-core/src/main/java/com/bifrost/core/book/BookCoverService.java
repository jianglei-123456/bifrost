package com.bifrost.core.book;

import com.bifrost.common.util.FileIO;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
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
 * 图书封面服务（M2-book）。
 *
 * <p>原图存 {@code cover-source/book-<id>.jpg}（与音乐 {@code al-<id>.jpg} 物理隔开，ADR-0004）；
 * 缩略图 Day-one 仅 200px 一档（Q16/Q20）。删除封面同时清缓存（Q29）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookCoverService {

    /** coverSource 标记：内嵌图（来自 EPUB 容器或 XMP 缩略图） */
    public static final String EMBEDDED = "EMBEDDED";

    /** coverSource 标记：用户上传（优先级高于 EMBEDDED；扫描遇 EMBEDDED 不覆盖） */
    public static final String UPLOADED = "UPLOADED";

    private final BifrostProperties properties;
    private final BookRepository bookRepository;

    public Path coverSourceDir() {
        return properties.getMedia().getCoverCacheDir().toAbsolutePath().normalize()
                .resolve("cover-source");
    }

    Path coverCacheDir() {
        return properties.getMedia().getCoverCacheDir().toAbsolutePath().normalize()
                .resolve("cover-cache");
    }

    /**
     * 存储内嵌封面（仅当图书尚无封面时）。
     *
     * @return 是否写入（false=已存在封面或无数据）
     */
    public boolean storeEmbeddedCover(Book book, byte[] coverData) {
        if (coverData == null || coverData.length == 0 || book.getId() == null) {
            return false;
        }
        if (book.getCoverSource() != null) {
            return false;
        }
        try {
            FileIO.ensureDirs(coverSourceDir());
            Path target = coverSourceDir().resolve("book-" + book.getId() + ".jpg");
            Files.write(target, coverData);
            book.setCoverSource(EMBEDDED);
            bookRepository.save(book);
            return true;
        } catch (IOException e) {
            log.warn("图书内嵌封面落盘失败: book={}", book.getId(), e);
            return false;
        }
    }

    /**
     * 存储用户上传封面（覆盖现有任何来源）。先 delete 再写，确保缓存重建。
     *
     * @return 写入字节数（0=失败）
     */
    public long storeUploadedCover(Book book, byte[] data) {
        if (book == null || book.getId() == null || data == null || data.length == 0) {
            return 0L;
        }
        // 先清旧（缓存一并）
        delete(book);
        // 等比缩小到 maxWidth=1200（避免大图占盘）；写为 JPEG
        byte[] jpeg;
        try {
            jpeg = normalizeToJpeg(data, 1200);
        } catch (IOException e) {
            log.warn("图书上传封面解码失败: book={} err={}", book.getId(), e.getMessage());
            return 0L;
        }
        try {
            FileIO.ensureDirs(coverSourceDir());
            Path target = coverSourceDir().resolve("book-" + book.getId() + ".jpg");
            Files.write(target, jpeg);
            book.setCoverSource(UPLOADED);
            bookRepository.save(book);
            return jpeg.length;
        } catch (IOException e) {
            log.warn("图书上传封面落盘失败: book={}", book.getId(), e);
            return 0L;
        }
    }

    /** 用 ImageIO 读 → 等比缩放到 maxWidth → JPEG 编码。失败抛异常由调用方 catch。 */
    private static byte[] normalizeToJpeg(byte[] data, int maxWidth) throws IOException {
        try (var in = new java.io.ByteArrayInputStream(data)) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                throw new IOException("无法识别图像格式");
            }
            BufferedImage target = src;
            if (src.getWidth() > maxWidth) {
                int h = (int) Math.round(src.getHeight() * (maxWidth / (double) src.getWidth()));
                target = new BufferedImage(maxWidth, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = target.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(src, 0, 0, maxWidth, h, null);
                g.dispose();
            }
            var out = new java.io.ByteArrayOutputStream();
            ImageIO.write(target, "jpg", out);
            return out.toByteArray();
        }
    }

    /**
     * 读取封面（可按 size 缩放）；无封面返回 empty（调用方按 OPDS 协议返回 404）。
     */
    public Optional<byte[]> coverData(Long bookId, Integer size) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null || book.getCoverSource() == null) {
            return Optional.empty();
        }
        Optional<Path> original = originalCover(book);
        if (original.isEmpty()) {
            return Optional.empty();
        }
        try {
            if (size == null || size <= 0) {
                return Optional.of(Files.readAllBytes(original.get()));
            }
            return Optional.of(thumbnail(book.getId(), original.get(), size));
        } catch (IOException e) {
            throw new UncheckedIOException("读取图书封面失败: book=" + bookId, e);
        }
    }

    private Optional<Path> originalCover(Book book) {
        if (!EMBEDDED.equals(book.getCoverSource())) {
            return Optional.empty();
        }
        Path path = coverSourceDir().resolve("book-" + book.getId() + ".jpg");
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    /** 缩略图：缓存命中直接返回；否则缩放生成并缓存（Day-one 仅 200px 一档）。 */
    private byte[] thumbnail(Long bookId, Path original, int requestedSize) throws IOException {
        int target = requestedSize <= 64 ? 64 : (requestedSize <= 200 ? 200 : requestedSize);
        FileIO.ensureDirs(coverCacheDir());
        Path cache = coverCacheDir().resolve("book-" + bookId + "-" + target + ".jpg");
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
            log.debug("图书缩略图缓存写入失败（忽略）: {}", cache, e);
        }
        return data;
    }

    private static byte[] toJpegBytes(BufferedImage image) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /**
     * 删除封面：移除 coverSource + 物理删除原图与所有缩略图缓存（Q29）。
     */
    public void delete(Book book) {
        if (book == null || book.getCoverSource() == null) {
            return;
        }
        try {
            Files.deleteIfExists(coverSourceDir().resolve("book-" + book.getId() + ".jpg"));
        } catch (IOException e) {
            log.debug("封面原图删除失败（忽略）: book={}", book.getId(), e);
        }
        try {
            Path dir = coverCacheDir();
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    String prefix = "book-" + book.getId() + "-";
                    stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                    // ignore
                                }
                            });
                }
            }
        } catch (IOException e) {
            log.debug("封面缓存清理失败（忽略）: book={}", book.getId(), e);
        }
        book.setCoverSource(null);
        bookRepository.save(book);
    }
}