package com.bifrost.core.audio.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 正在播放（nowPlaying）进程内存储。
 *
 * <p>v1 单用户进程内 Map（key=trackId）；记录来源：stream 开始 + scrobble(submission=false)（Q20）。
 * playerId 取 Subsonic 客户端 c 参数；minutesAgo 由调用方按 startTime 计算。</p>
 */
@Service
public class NowPlayingService {

    /** 正在播放条目 */
    public record NowPlayingEntry(Long trackId, String title, String username, String playerId, Instant startTime) {
    }

    private final Map<Long, NowPlayingEntry> playing = new ConcurrentHashMap<>();

    public void record(Long trackId, String title, String username, String playerId, Instant startTime) {
        playing.put(trackId, new NowPlayingEntry(trackId, title, username, playerId, startTime));
    }

    /** 当前正在播放列表（按开始时间倒序）。 */
    public List<NowPlayingEntry> list() {
        return playing.values().stream()
                .sorted(Comparator.comparing(NowPlayingEntry::startTime).reversed())
                .toList();
    }

    public void clear() {
        playing.clear();
    }
}
