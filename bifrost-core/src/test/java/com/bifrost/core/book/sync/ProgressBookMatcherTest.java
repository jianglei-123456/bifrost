package com.bifrost.core.book.sync;

import com.bifrost.core.book.DocumentFingerprint;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 指纹 → 图书 匹配器测试（M3-sync T1.4/T1.5）。
 */
class ProgressBookMatcherTest {

    private final BookRepository bookRepository = mock(BookRepository.class);
    private final ProgressBookMatcher matcher = new ProgressBookMatcher(bookRepository);

    private static Book book(long id, String filePath, String title, String extension) {
        Book book = new Book();
        book.setId(id);
        book.setFilePath(filePath);
        book.setTitle(title);
        book.setExtension(extension);
        book.setIsAvailable(true);
        return book;
    }

    @Test
    void matchReturnsSmallestIdAmongDuplicates() {
        Book first = book(1L, "/library/a/copy.epub", "Copy", "epub");
        Book second = book(2L, "/library/b/copy.epub", "Copy", "epub");
        when(bookRepository.findByPartialMd5OrderByIdAsc("abc123")).thenReturn(List.of(first, second));

        assertThat(matcher.match("abc123")).contains(first);
    }

    @Test
    void matchBlankOrNullReturnsEmptyWithoutHittingRepository() {
        assertThat(matcher.match(null)).isEmpty();
        assertThat(matcher.match("   ")).isEmpty();
        verifyNoInteractions(bookRepository);
    }

    @Test
    void matchUnknownFingerprintReturnsEmpty() {
        when(bookRepository.findByPartialMd5OrderByIdAsc("deadbeef")).thenReturn(List.of());

        assertThat(matcher.match("deadbeef")).isEmpty();
    }

    @Test
    void suggestPrefersFilenameFingerprintOverOpdsName() {
        // 库内文件名与"标题.扩展名"故意不同，才能区分两种候选来源
        Book candidate = book(9L, "/library/Author/pride_prejudice_v2.epub", "Pride and Prejudice", "epub");
        when(bookRepository.findByIsAvailableTrueOrderByIdAsc()).thenReturn(List.of(candidate));

        String filenameMd5 = DocumentFingerprint.fileNameMd5(Path.of(candidate.getFilePath()));
        String opdsMd5 = DocumentFingerprint.opdsServedNameMd5("Pride and Prejudice", "epub");

        assertThat(filenameMd5).isNotEqualTo(opdsMd5);
        assertThat(matcher.suggest(filenameMd5)).get()
                .satisfies(s -> {
                    assertThat(s.bookId()).isEqualTo(9L);
                    assertThat(s.reason()).isEqualTo("FILENAME");
                });
        assertThat(matcher.suggest(opdsMd5)).get()
                .satisfies(s -> assertThat(s.reason()).isEqualTo("OPDS_NAME"));
    }

    @Test
    void suggestAllLoadsLibraryOnceAndSkipsUnknownFingerprints() {
        Book candidate = book(4L, "/library/Some Book.epub", "Some Book", "epub");
        when(bookRepository.findByIsAvailableTrueOrderByIdAsc()).thenReturn(List.of(candidate));
        String known = DocumentFingerprint.fileNameMd5(Path.of(candidate.getFilePath()));

        // Arrays.asList 允许 null 元素（List.of 不允许）
        Map<String, ProgressBookMatcher.Suggestion> result =
                matcher.suggestAll(Arrays.asList(known, "ffff", "", null));

        assertThat(result).containsOnlyKeys(known);
        assertThat(result.get(known).bookId()).isEqualTo(4L);
        verify(bookRepository, times(1)).findByIsAvailableTrueOrderByIdAsc();
    }

    @Test
    void suggestAllWithNoFingerprintsDoesNotTouchRepository() {
        assertThat(matcher.suggestAll(List.of())).isEmpty();
        verifyNoInteractions(bookRepository);
    }
}
