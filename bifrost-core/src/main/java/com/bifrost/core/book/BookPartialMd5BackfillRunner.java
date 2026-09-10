package com.bifrost.core.book;

import com.bifrost.core.config.BifrostProperties;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时回填 {@code book.partial_md5}（M3-sync T1.3，R7）。
 *
 * <p>覆盖"从未被扫描碰到"的书：增量扫描按 {@code fingerprint}（path+size+mtime）跳过，
 * 加了新列也不会自动重算；库根被禁用或目录不可达时扫描自愈也帮不上忙，故启动兜底一次。</p>
 *
 * <p>成本：每本书只读 12KB；无缺失时仅一条 count 查询。<b>文件 IO 在事务外</b>，
 * 更新按 {@code bifrost.scan.batch-size} 分批提交，绝不把全库 IO 包进一个长事务。
 * 任何失败都不阻断启动（下次启动或扫描时自愈）。</p>
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class BookPartialMd5BackfillRunner implements ApplicationRunner {

    private final BookRepository bookRepository;
    private final PlatformTransactionManager transactionManager;
    private final BifrostProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill();
        } catch (RuntimeException e) {
            log.warn("回填文档指纹失败（不影响启动）: {}", e.toString());
        }
    }

    private void backfill() {
        long missing = bookRepository.countByPartialMd5IsNull();
        if (missing == 0) {
            return;
        }
        log.info("回填文档指纹(partial_md5)：待处理 {} 本", missing);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int batchSize = Math.max(1, properties.getScan().getBatchSize());
        int filled = 0;
        int skipped = 0;
        while (true) {
            Page<Book> page = bookRepository.findByPartialMd5IsNull(
                    PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "id")));
            List<Book> batch = page.getContent();
            if (batch.isEmpty()) {
                break;
            }
            List<Book> toUpdate = new ArrayList<>();
            for (Book book : batch) {
                String md5 = compute(book.getFilePath());
                if (md5 == null) {
                    skipped++;
                    continue;
                }
                book.setPartialMd5(md5);
                toUpdate.add(book);
            }
            if (toUpdate.isEmpty()) {
                // 本页全部跳过（文件不存在/不可读）→ 再查还是同一页，必须退出防死循环
                break;
            }
            tx.executeWithoutResult(status -> bookRepository.saveAll(toUpdate));
            filled += toUpdate.size();
        }
        log.info("回填文档指纹完成：成功 {} 本，跳过 {} 本（跳过的会在下次扫描时自愈）", filled, skipped);
    }

    private String compute(String filePath) {
        if (filePath == null) {
            return null;
        }
        try {
            return DocumentFingerprint.partialMd5(Path.of(filePath));
        } catch (RuntimeException e) {
            log.warn("文档指纹回填跳过 {}: {}", filePath, e.toString());
            return null;
        }
    }
}
