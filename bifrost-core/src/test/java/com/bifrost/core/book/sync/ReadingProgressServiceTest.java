package com.bifrost.core.book.sync;

import com.bifrost.domain.entity.ReadingProgress;
import com.bifrost.domain.entity.SyncDevice;
import com.bifrost.domain.repo.ReadingProgressRepository;
import com.bifrost.domain.repo.SyncDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 阅读进度服务测试（M3-sync T1.4）：后写覆盖语义、首次建行判定、设备计数。
 */
class ReadingProgressServiceTest {

    private final ReadingProgressRepository progressRepository = mock(ReadingProgressRepository.class);
    private final SyncDeviceRepository deviceRepository = mock(SyncDeviceRepository.class);
    private final ProgressBookMatcher matcher = mock(ProgressBookMatcher.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private final ReadingProgressService service =
            new ReadingProgressService(progressRepository, deviceRepository, matcher, transactionManager);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(progressRepository.saveAndFlush(any(ReadingProgress.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.save(any(SyncDevice.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void firstPushCreatesRowAndReportsFirstAppearance() {
        when(progressRepository.findBySyncAccountIdAndDocumentFingerprint(1L, "doc")).thenReturn(Optional.empty());
        when(deviceRepository.findBySyncAccountIdAndDeviceId(1L, "dev-1")).thenReturn(Optional.empty());

        ReadingProgressService.ProgressUpsert result =
                service.upsert(1L, "doc", "/body/DocFragment[2]/body/p[1].0", 0.25, "Kobo", "dev-1");

        assertThat(result.firstAppearance()).isTrue();
        ReadingProgress row = result.progress();
        assertThat(row.getSyncAccountId()).isEqualTo(1L);
        assertThat(row.getDocumentFingerprint()).isEqualTo("doc");
        assertThat(row.getProgress()).isEqualTo("/body/DocFragment[2]/body/p[1].0");
        assertThat(row.getPercentage()).isEqualTo(0.25);
        assertThat(row.getDevice()).isEqualTo("Kobo");
        assertThat(row.getDeviceId()).isEqualTo("dev-1");
        assertThat(row.getIgnored()).isFalse();
        assertThat(row.getBookId()).isNull();
        // timestamp 必须是秒级（毫秒会让客户端判定"永远更新"）
        assertThat(row.getReportedAt().getNano()).isZero();
    }

    @Test
    void repeatedPushOverwritesEvenWhenOlderAndKeepsMatchState() {
        ReadingProgress existing = new ReadingProgress();
        existing.setId(7L);
        existing.setSyncAccountId(1L);
        existing.setDocumentFingerprint("doc");
        existing.setProgress("999");
        existing.setPercentage(0.9);
        existing.setBookId(42L);
        existing.setIgnored(true);
        Instant settled = Instant.ofEpochSecond(1_700_000_000L);
        existing.setScanAttemptedAt(settled);
        when(progressRepository.findBySyncAccountIdAndDocumentFingerprint(1L, "doc"))
                .thenReturn(Optional.of(existing));
        SyncDevice device = new SyncDevice();
        device.setReportCount(4L);
        when(deviceRepository.findBySyncAccountIdAndDeviceId(1L, "dev-1")).thenReturn(Optional.of(device));

        ReadingProgressService.ProgressUpsert result =
                service.upsert(1L, "doc", "12", 0.05, "Kindle", "dev-1");

        assertThat(result.firstAppearance()).isFalse();
        ReadingProgress row = result.progress();
        // 更旧的位置照样覆盖（协议语义：服务端不比较新旧）
        assertThat(row.getProgress()).isEqualTo("12");
        assertThat(row.getPercentage()).isEqualTo(0.05);
        assertThat(row.getDevice()).isEqualTo("Kindle");
        // 匹配状态不被上报载荷影响
        assertThat(row.getBookId()).isEqualTo(42L);
        assertThat(row.getScanAttemptedAt()).isEqualTo(settled);
        assertThat(row.getIgnored()).isTrue();
        // 设备计数自增、名字更新
        assertThat(device.getReportCount()).isEqualTo(5L);
        assertThat(device.getDeviceName()).isEqualTo("Kindle");
    }

    @Test
    void concurrentFirstPushRetriesAfterUniqueConstraintViolation() {
        // 第一次查询返回空（撞唯一约束由数据库抛出），重试时已能查到行
        ReadingProgress existing = new ReadingProgress();
        existing.setId(9L);
        existing.setSyncAccountId(1L);
        existing.setDocumentFingerprint("doc");
        when(progressRepository.findBySyncAccountIdAndDocumentFingerprint(1L, "doc"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(progressRepository.saveAndFlush(any(ReadingProgress.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("unique"))
                .thenAnswer(inv -> inv.getArgument(0));
        when(deviceRepository.findBySyncAccountIdAndDeviceId(1L, "dev-1")).thenReturn(Optional.empty());

        ReadingProgressService.ProgressUpsert result =
                service.upsert(1L, "doc", "5", 0.1, "Kobo", "dev-1");

        assertThat(result.firstAppearance()).isFalse();
        assertThat(result.progress().getId()).isEqualTo(9L);
        assertThat(result.progress().getProgress()).isEqualTo("5");
    }
}
