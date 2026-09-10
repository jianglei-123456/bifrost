package com.bifrost.core.book.sync;

import com.bifrost.core.book.DocumentFingerprint;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档指纹 → 图书 的匹配器（M3-sync T1.4/T1.5，Q2-B 的落点）。
 *
 * <p><b>主匹配</b>：内容指纹（{@code book.partial_md5}）相等即命中。同一份文件在库里可能有多份副本
 * （不同图书目录各一份），取 <b>id 最小</b> 的那本，保证确定性。</p>
 *
 * <p><b>建议绑定</b>（R5-d）：用文件名模式指纹给孤儿一个<b>候选</b>——客户端
 * {@code checksum_method=FILENAME} 时 {@code document = md5(basename)}；OPDS 下发到设备的文件名则是
 * {@code 标题.扩展名}。两条都<b>只建议、不自动绑</b>：设备上的文件名可能被客户端改写，也可能被用户改过。</p>
 */
@Service
@RequiredArgsConstructor
public class ProgressBookMatcher {

    private final BookRepository bookRepository;

    /** 主匹配：内容指纹命中（未命中返回 empty，调用方决定是孤儿还是待结算）。 */
    public Optional<Book> match(String documentFingerprint) {
        if (isBlank(documentFingerprint)) {
            return Optional.empty();
        }
        List<Book> books = bookRepository.findByPartialMd5OrderByIdAsc(documentFingerprint);
        return books.isEmpty() ? Optional.empty() : Optional.of(books.get(0));
    }

    /** 单条建议绑定。 */
    public Optional<Suggestion> suggest(String documentFingerprint) {
        return Optional.ofNullable(suggestAll(List.of(documentFingerprint == null ? "" : documentFingerprint))
                .get(documentFingerprint));
    }

    /**
     * 批量建议绑定（孤儿列表整页一次算完，避免 N 次全库遍历）。
     *
     * @return 指纹 → 建议（无建议的指纹不在 map 里）
     */
    public Map<String, Suggestion> suggestAll(Collection<String> documentFingerprints) {
        if (documentFingerprints == null || documentFingerprints.isEmpty()) {
            return Map.of();
        }
        Set<String> wanted = documentFingerprints.stream()
                .filter(f -> !isBlank(f))
                .collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return Map.of();
        }
        Map<String, Suggestion> result = new HashMap<>();
        List<Book> books = bookRepository.findByIsAvailableTrueOrderByIdAsc();
        // 第一轮：basename 指纹（优先级更高——它更可能是"同一份文件"）
        for (Book book : books) {
            String md5 = safeFileNameMd5(book);
            if (md5 != null && wanted.contains(md5) && !result.containsKey(md5)) {
                result.put(md5, new Suggestion(book.getId(), book.getTitle(), "FILENAME"));
            }
        }
        // 第二轮：OPDS 下发文件名指纹
        for (Book book : books) {
            String md5 = DocumentFingerprint.opdsServedNameMd5(book.getTitle(), book.getExtension());
            if (md5 != null && wanted.contains(md5) && !result.containsKey(md5)) {
                result.put(md5, new Suggestion(book.getId(), book.getTitle(), "OPDS_NAME"));
            }
        }
        return result;
    }

    private String safeFileNameMd5(Book book) {
        try {
            return DocumentFingerprint.fileNameMd5(Path.of(book.getFilePath()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 建议绑定结果（{@code reason} = FILENAME / OPDS_NAME）。 */
    public record Suggestion(Long bookId, String title, String reason) {
    }
}
