package com.bifrost.core.audio.service;

import com.bifrost.domain.entity.PlayQueue;
import com.bifrost.domain.entity.PlayQueueEntry;
import com.bifrost.domain.repo.PlayQueueEntryRepository;
import com.bifrost.domain.repo.PlayQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 播放队列服务（Subsonic getPlayQueue/savePlayQueue）。
 *
 * <p>savePlayQueue 为全量重建：删除旧队列行及其条目后按提交顺序重插（changed=新行创建时间，
 * 保证每次保存都刷新）。空曲目列表 = 清空队列（仅删除，不留空行）。</p>
 */
@Service
@RequiredArgsConstructor
public class PlayQueueService {

    private final PlayQueueRepository playQueueRepository;
    private final PlayQueueEntryRepository entryRepository;

    /** 播放队列及条目视图 */
    public record PlayQueueView(PlayQueue queue, List<PlayQueueEntry> entries) {
    }

    /** 用户播放队列（无则空）。 */
    @Transactional(readOnly = true)
    public Optional<PlayQueueView> get(Long userId) {
        return playQueueRepository.findByUserId(userId)
                .map(q -> new PlayQueueView(q, entryRepository.findByQueueIdOrderBySeqAsc(q.getId())));
    }

    /**
     * 全量保存播放队列。
     *
     * @param trackIds       队列曲目（按客户端顺序）；空 = 清空队列
     * @param currentTrackId 当前曲目（可空）
     * @param position       当前曲目内位置（可空，原样回显）
     */
    @Transactional
    public void save(Long userId, List<Long> trackIds, Long currentTrackId, Long position) {
        playQueueRepository.findByUserId(userId).ifPresent(queue -> {
            entryRepository.deleteByQueueId(queue.getId());
            playQueueRepository.delete(queue);
            playQueueRepository.flush(); // 立即落删除：避免新行 INSERT 与旧行唯一键(user_id)冲突
        });
        if (trackIds == null || trackIds.isEmpty()) {
            return; // 清空
        }
        PlayQueue queue = new PlayQueue();
        queue.setUserId(userId);
        queue.setCurrentTrackId(currentTrackId);
        queue.setPosition(position);
        playQueueRepository.save(queue);
        int seq = 1;
        for (Long trackId : trackIds) {
            PlayQueueEntry entry = new PlayQueueEntry();
            entry.setQueueId(queue.getId());
            entry.setTrackId(trackId);
            entry.setSeq(seq++);
            entryRepository.save(entry);
        }
    }
}
