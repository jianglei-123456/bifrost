package com.bifrost.core.audio.service;

import com.bifrost.common.exception.BizException;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.Artist;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import com.bifrost.domain.repo.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 标注服务：三态收藏（star/unstar）与评分（rating 1–5，0=取消）。
 *
 * <p>曲目/专辑/艺术家三态（Q8/ADR-0001）；starred 用 {@code starredAt}（Q14）。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnotationService {

    /** 标注对象类型：track / album / artist */
    public static final String TYPE_TRACK = "track";
    public static final String TYPE_ALBUM = "album";
    public static final String TYPE_ARTIST = "artist";

    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;

    @Transactional
    public void star(String type, Long id) {
        setStarred(type, id, true);
    }

    @Transactional
    public void unstar(String type, Long id) {
        setStarred(type, id, false);
    }

    private void setStarred(String type, Long id, boolean starred) {
        switch (type) {
            case TYPE_TRACK -> {
                Track t = requireTrack(id);
                t.setStarredAt(starred && t.getStarredAt() == null ? Instant.now() : (starred ? t.getStarredAt() : null));
                trackRepository.save(t);
            }
            case TYPE_ALBUM -> {
                Album a = requireAlbum(id);
                a.setStarredAt(starred && a.getStarredAt() == null ? Instant.now() : (starred ? a.getStarredAt() : null));
                albumRepository.save(a);
            }
            case TYPE_ARTIST -> {
                Artist ar = requireArtist(id);
                ar.setStarredAt(starred && ar.getStarredAt() == null ? Instant.now() : (starred ? ar.getStarredAt() : null));
                artistRepository.save(ar);
            }
            default -> throw BizException.paramError("不支持的标注类型: " + type);
        }
    }

    /** 评分 1–5（0=取消）。 */
    @Transactional
    public void rate(String type, Long id, int rating) {
        if (rating < 0 || rating > 5) {
            throw BizException.paramError("评分必须在 0–5 之间");
        }
        switch (type) {
            case TYPE_TRACK -> {
                Track t = requireTrack(id);
                t.setRating(rating);
                trackRepository.save(t);
            }
            case TYPE_ALBUM -> {
                Album a = requireAlbum(id);
                a.setRating(rating);
                albumRepository.save(a);
            }
            case TYPE_ARTIST -> {
                Artist ar = requireArtist(id);
                ar.setRating(rating);
                artistRepository.save(ar);
            }
            default -> throw BizException.paramError("不支持的标注类型: " + type);
        }
    }

    private Track requireTrack(Long id) {
        return trackRepository.findById(id).orElseThrow(() -> BizException.notFound("曲目不存在: " + id));
    }

    private Album requireAlbum(Long id) {
        return albumRepository.findById(id).orElseThrow(() -> BizException.notFound("专辑不存在: " + id));
    }

    private Artist requireArtist(Long id) {
        return artistRepository.findById(id).orElseThrow(() -> BizException.notFound("艺术家不存在: " + id));
    }
}
