package com.bifrost.core.book;

import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.FileIO;
import com.bifrost.common.util.Fingerprint;
import com.bifrost.core.book.event.BookScanCompletedEvent;
import com.bifrost.core.book.model.BookResult;
import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.event.ScanStats;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.LibraryRoot;
import com.bifrost.domain.enums.MediaType;
import com.bifrost.domain.enums.ScanStatus;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.LibraryRootRepository;
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
 * 图书扫描引擎（M2-book，{@code doc/m2-book/task/01-图书核心.md} T1.3）。
 *
 * <p>与 {@code MusicScanService} 物理隔开：独立 {@link ReentrantLock}（未来允许图书与音乐并行扫描，
 * ADR-0004）；仅扫描 {@link MediaType#BOOK} 图书目录；批事务 200 文件/批；
 * 不聚合（Book 单层，无 Album/Artist，Q3-B 决策）。
 * 解析异常 → {@link BookResult#fallback}，绝不抛。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookScanService {

    /** 图书扫描互斥锁（与 MusicScanService 独立；未来允许同时扫图书与音乐） */
    private final ReentrantLock bookScanLock = new ReentrantLock();

    private final LibraryRootRepository libraryRootRepository;
    private final BookRepository bookRepository;
    private final BookParserRegistry parserRegistry;
    private final BookCoverService bookCoverService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;
    private final BifrostProperties properties;

    /** 扫描所有启用的图书目录（增量） */
    public ScanStats scanAll() {
        return scanAll(false);
    }

    /**
     * 扫描所有启用的图书目录（串行）。
     *
     * @param force true=强制全量重解析（重读所有元数据）；false=增量（指纹一致跳过）
     */
    public ScanStats scanAll(boolean force) {
        List<LibraryRoot> roots = libraryRootRepository.findByMediaTypeAndEnabledTrueOrderByIdAsc(MediaType.BOOK);
        int added = 0, updated = 0, missing = 0, error = 0;
        for (LibraryRoot root : roots) {
            ScanStats s = scanRoot(root.getId(), force);
            added += s.added();
            updated += s.updated();
            missing += s.missing();
            error += s.error();
        }
        return new ScanStats(added, updated, missing, error);
    }

    /** 扫描单个图书目录（增量） */
    public ScanStats scanRoot(Long rootId) {
        return scanRoot(rootId, false);
    }

    /**
     * 扫描单个图书目录（必须为 {@link MediaType#BOOK} 类型）。
     *
     * @param force true=全量重解析；false=增量
     * @throws com.bifrost.common.exception.BizException 1100 扫描进行中；1001 图书目录不存在；
     *                                                     1002 目录非 BOOK 类型
     */
    public ScanStats scanRoot(Long rootId, boolean force) {
        if (!bookScanLock.tryLock()) {
            throw BizException.scanInProgress("图书扫描进行中");
        }
        try {
            LibraryRoot root = libraryRootRepository.findById(rootId)
                    .orElseThrow(() -> BizException.notFound("图书目录不存在: " + rootId));
            if (root.getMediaType() != MediaType.BOOK) {
                throw BizException.paramError("该目录非图书类型: " + root.getName());
            }
            if (!Boolean.TRUE.equals(root.getEnabled())) {
                throw BizException.paramError("图书目录已禁用: " + root.getName());
            }
            root.setScanStatus(ScanStatus.SCANNING);
            libraryRootRepository.save(root);
            ScanStats stats;
            try {
                stats = scanLibraryRoot(root, force);
            } catch (RuntimeException e) {
                // 与 MusicScanService 同款：异常时必须清回 IDLE，否则图书目录永久停在
                // SCANNING（异步触发路径会吞掉异常，管理端扫描条将一直显示"扫描中"）。
                root.setScanStatus(ScanStatus.IDLE);
                libraryRootRepository.save(root);
                throw e;
            }
            root.setScanStatus(ScanStatus.IDLE);
            root.setLastScanAt(Instant.now());
            root.setLastScanStats(toJson(stats));
            libraryRootRepository.save(root);
            eventPublisher.publishEvent(new BookScanCompletedEvent(root.getId(), stats));
            return stats;
        } finally {
            bookScanLock.unlock();
        }
    }

    /** 单图书目录扫描主体 */
    private ScanStats scanLibraryRoot(LibraryRoot root, boolean force) {
        Path dir = Path.of(root.getPath());
        if (!Files.isDirectory(dir)) {
            log.warn("图书目录不存在，跳过扫描: {}", root.getPath());
            return new ScanStats(0, 0, 0, 1);
        }
        Map<String, Book> dbIndex = bookRepository.findByLibraryRootId(root.getId()).stream()
                .collect(Collectors.toMap(Book::getFilePath, Function.identity()));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int batchSize = Math.max(1, properties.getScan().getBatchSize());
        Counters counters = new Counters();
        List<PendingBook> pending = new ArrayList<>();

        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> parserRegistry.find(FileIO.extension(p)).isPresent())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("遍历图书目录失败: " + root.getPath(), e);
        }

        for (Path file : files) {
            String abs = file.toAbsolutePath().normalize().toString();
            Book existing = dbIndex.remove(abs);
            String fingerprint = Fingerprint.of(file);
            if (!force && existing != null && Boolean.TRUE.equals(existing.getIsAvailable())
                    && fingerprint.equals(existing.getFingerprint())) {
                continue;
            }
            BookResult result = parserRegistry.parse(file);
            if (result.parseError()) {
                counters.error++;
            }
            pending.add(new PendingBook(existing != null ? existing : new Book(), result, file, abs,
                    fingerprint, root.getId()));
            if (existing == null) {
                counters.added++;
            } else {
                counters.updated++;
            }
            if (pending.size() >= batchSize) {
                flush(tx, pending);
            }
        }
        flush(tx, pending);

        // 剩余 dbIndex = 文件已消失
        List<Book> remaining = new ArrayList<>(dbIndex.values());
        if (!remaining.isEmpty()) {
            tx.executeWithoutResult(status -> {
                for (Book b : remaining) {
                    if (Boolean.TRUE.equals(b.getIsAvailable())) {
                        b.setIsAvailable(false);
                        bookRepository.save(b);
                        counters.missing++;
                    }
                }
            });
        }

        log.info("图书扫描完成: root={} added={} updated={} missing={} error={}",
                root.getName(), counters.added, counters.updated, counters.missing, counters.error);
        return new ScanStats(counters.added, counters.updated, counters.missing, counters.error);
    }

    /** 批事务提交（200 文件/批） */
    private void flush(TransactionTemplate tx, List<PendingBook> pending) {
        if (pending.isEmpty()) {
            return;
        }
        List<PendingBook> batch = List.copyOf(pending);
        pending.clear();
        tx.executeWithoutResult(status -> {
            for (PendingBook p : batch) {
                Book b = p.book();
                applyFields(b, p.result(), p.file(), p.absPath(), p.fingerprint(), p.libraryRootId());
                bookRepository.save(b);
                // 封面：内嵌图（仅首次，storeEmbeddedCover 内部会判 coverSource==null 跳过）
                bookCoverService.storeEmbeddedCover(b, p.result().embeddedCover());
            }
        });
    }

    private void applyFields(Book b, BookResult r, Path file, String absPath, String fingerprint, Long rootId) {
        b.setFilePath(absPath);
        b.setFileSize(FileIO.size(file));
        b.setFileLastModified(FileIO.lastModifiedMillis(file));
        b.setFingerprint(fingerprint);
        b.setTitle(r.title());
        b.setAuthors(r.authors());
        b.setLanguage(r.language());
        b.setPublisher(r.publisher());
        b.setPubDate(r.pubDate());
        b.setDescription(r.description());
        b.setSubject(r.subject());
        b.setIdentifier(r.identifier());
        b.setSeries(r.series());
        b.setSeriesIndex(r.seriesIndex());
        b.setRights(r.rights());
        b.setFormat(r.format());
        b.setExtension(FileIO.extension(file));
        b.setLibraryRootId(rootId);
        b.setIsAvailable(true);
        // embeddedCover / coverSource 由 BookCoverService 单独管
        if (b.getId() == null) {
            b.setCoverSource(null);
        }
    }

    private static String toJson(ScanStats stats) {
        return "{\"added\":" + stats.added() + ",\"updated\":" + stats.updated()
                + ",\"missing\":" + stats.missing() + ",\"error\":" + stats.error() + "}";
    }

    private record PendingBook(Book book, BookResult result, Path file, String absPath,
                               String fingerprint, Long libraryRootId) {
    }

    private static final class Counters {
        int added;
        int updated;
        int missing;
        int error;
    }
}