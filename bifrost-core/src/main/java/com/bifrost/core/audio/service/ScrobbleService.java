package com.bifrost.core.audio.service;

import com.bifrost.common.exception.BizException;
import com.bifrost.core.audio.AggregationService;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.repo.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 播放统计服务（scrobble）。
 *
 * <p>仅 {@code submission=true} 落库 playCount/lastPlayed（OpenSubsonic 澄清）；
 * stream 调用不计数。nowPlaying 双来源：stream 开始 + scrobble(submission=false)（Q20）。</p>
 */
@Service
@RequiredArgsConstructor
public class ScrobbleService {

    private final TrackRepository trackRepository;
    private final AggregationService aggregationService;
    private final NowPlayingService nowPlayingService;

    /**
     * 播放上报。
     *
     * @param trackId    曲目 ID
     * @param time       播放时间（毫秒时间戳，可空=当前）
     * @param submission true=计入播放统计；false=仅"正在播放"通知
     * @param username   用户（v1=admin）
     * @param playerId   播放器标识（Subsonic 客户端 c 参数，Q20）
     */
    @Transactional
    public void scrobble(Long trackId, Long time, boolean submission, String username, String playerId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> BizException.notFound("曲目不存在: " + trackId));
        Instant playedAt = time != null ? Instant.ofEpochMilli(time) : Instant.now();
        if (submission) {
            track.setPlayCount((track.getPlayCount() == null ? 0 : track.getPlayCount()) + 1);
            track.setLastPlayed(playedAt);
            trackRepository.save(track);
            // 专辑聚合播放统计同步刷新
            if (track.getAlbumId() != null) {
                aggregationService.refreshAlbumAggregates(java.util.List.of(track.getAlbumId()));
            }
        }
        nowPlayingService.record(track.getId(), track.getTitle(), username, playerId, playedAt);
    }
}
