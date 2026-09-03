package com.bifrost.core.audio.service;

import com.bifrost.domain.entity.Bookmark;
import com.bifrost.domain.repo.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 书签服务（Subsonic bookmark 系列）。
 *
 * <p>每用户每曲目至多一个书签：createBookmark 语义为 upsert（position/comment 更新，
 * 曲目不换则复用原记录，created 不变、changed 刷新）。曲目失效时记录保留，展示层隐藏。</p>
 */
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;

    /** 用户全部书签（新→旧）。 */
    @Transactional(readOnly = true)
    public List<Bookmark> listByUser(Long userId) {
        return bookmarkRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /** 创建或更新用户对某曲目的书签。 */
    @Transactional
    public Bookmark save(Long userId, Long trackId, Long position, String comment) {
        Bookmark bookmark = bookmarkRepository.findByUserIdAndTrackId(userId, trackId).orElseGet(Bookmark::new);
        bookmark.setUserId(userId);
        bookmark.setTrackId(trackId);
        bookmark.setPosition(position);
        bookmark.setComment(comment);
        return bookmarkRepository.save(bookmark);
    }

    /** 删除用户对某曲目的书签（不存在时幂等无操作）。 */
    @Transactional
    public void delete(Long userId, Long trackId) {
        bookmarkRepository.deleteByUserIdAndTrackId(userId, trackId);
    }
}
