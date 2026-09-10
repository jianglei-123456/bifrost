package com.bifrost.core.book.sync;

import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.enums.ProgressMatchSource;
import com.bifrost.domain.repo.BookRepository;
import com.bifrost.domain.repo.ReadingProgressRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 孤儿进度服务测试（M3-sync T1.5）：结算、人工绑定、重新匹配、忽略。
 */
class OrphanProgressServiceTest {

    private final ReadingProgressRepository progressRepository = mock(ReadingProgressRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final ProgressBookMatcher matcher = mock(ProgressBookMatcher.class);

    private final OrphanProgressService service =
            new OrphanProgressService(progressRepository, bookRepository, matcher);

    private static ReadingProgress progress(long id, String fingerprint) {
        ReadingProgress row = new ReadingProgress();
        row.setId(id);
        row.setSyncAccountId(1L);
        row.setDocumentFingerprint(fingerprint);
        row.setProgress("10");
        row.setPercentage(0.1);
        row.setDevice("Kobo");
        row.setDeviceId("dev-1");
        row.setIgnored(false);
        return row;
    }

    private static Book book(long id, boolean available) {
        Book book = new Book();
        book.setId(id);
        book.setTitle("Some Book");
        book.setIsAvailable(available);
        return book;
    }

    @Test
    void settleBindsMatchesAndFreezesNonMatches() {
        ReadingProgress matched = progress(1L, "known");
        ReadingProgress unmatched = progress(2L, "unknown");
        when(progressRepository.findBySyncAccountIdAndBookIdIsNullAndScanAttemptedAtIsNull(1L))
                .thenReturn(List.of(matched, unmatched));
        when(matcher.match("known")).thenReturn(Optional.of(book(42L, true)));
        when(matcher.match("unknown")).thenReturn(Optional.empty());
        when(progressRepository.save(any(ReadingProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        int bound = service.settlePending(1L);

        assertThat(bound).isEqualTo(1);
        assertThat(matched.getBookId()).isEqualTo(42L);
        assertThat(matched.getMatchSource()).isEqualTo(ProgressMatchSource.AUTO);
        assertThat(matched.getScanAttemptedAt()).isNotNull();
        // 未命中 → 固定孤儿：既没绑定，也标记为"已结算"（不会再被自动重试）
        assertThat(unmatched.getBookId()).isNull();
        assertThat(unmatched.getScanAttemptedAt()).isNotNull();
    }

    @Test
    void settleWithNothingPendingDoesNothing() {
        when(progressRepository.findBySyncAccountIdAndBookIdIsNullAndScanAttemptedAtIsNull(1L))
                .thenReturn(List.of());

        assertThat(service.settlePending(1L)).isZero();
    }

    @Test
    void bindRejectsUnavailableBook() {
        when(progressRepository.findById(1L)).thenReturn(Optional.of(progress(1L, "doc")));
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book(5L, false)));

        assertThatThrownBy(() -> service.bind(1L, 5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("文件缺失");
    }

    @Test
    void bindMarksManualSourceAndClearsIgnored() {
        ReadingProgress row = progress(1L, "doc");
        row.setIgnored(true);
        when(progressRepository.findById(1L)).thenReturn(Optional.of(row));
        when(bookRepository.findById(5L)).thenReturn(Optional.of(book(5L, true)));
        when(progressRepository.save(any(ReadingProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        ReadingProgress bound = service.bind(1L, 5L);

        assertThat(bound.getBookId()).isEqualTo(5L);
        assertThat(bound.getMatchSource()).isEqualTo(ProgressMatchSource.MANUAL);
        assertThat(bound.getIgnored()).isFalse();
        assertThat(bound.getScanAttemptedAt()).isNotNull();
    }

    @Test
    void rematchWithoutHitIsNotAnError() {
        ReadingProgress row = progress(1L, "doc");
        when(progressRepository.findById(1L)).thenReturn(Optional.of(row));
        when(matcher.match("doc")).thenReturn(Optional.empty());
        when(progressRepository.save(any(ReadingProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        OrphanProgressService.RematchResult result = service.rematch(1L);

        assertThat(result.matched()).isFalse();
        assertThat(result.progress()).isNull();
        assertThat(row.getScanAttemptedAt()).isNotNull();
    }

    @Test
    void rematchWithHitBindsAutomatically() {
        ReadingProgress row = progress(1L, "doc");
        when(progressRepository.findById(1L)).thenReturn(Optional.of(row));
        when(matcher.match("doc")).thenReturn(Optional.of(book(8L, true)));
        when(progressRepository.save(any(ReadingProgress.class))).thenAnswer(inv -> inv.getArgument(0));

        OrphanProgressService.RematchResult result = service.rematch(1L);

        assertThat(result.matched()).isTrue();
        assertThat(result.progress()).isNotNull();
        assertThat(row.getBookId()).isEqualTo(8L);
        assertThat(row.getMatchSource()).isEqualTo(ProgressMatchSource.AUTO);
    }

    @Test
    void missingProgressRowReportsNotFound() {
        when(progressRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setIgnored(404L, true))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("进度记录不存在");
    }
}
