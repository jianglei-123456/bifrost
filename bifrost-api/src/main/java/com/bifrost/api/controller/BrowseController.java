package com.bifrost.api.controller;

import com.bifrost.api.dto.ArtistView;
import com.bifrost.api.response.ApiResponse;
import com.bifrost.api.response.PageResult;
import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.model.AlbumListType;
import com.bifrost.core.audio.service.LibraryQueryService;
import com.bifrost.domain.entity.Album;
import com.bifrost.domain.entity.Artist;
import com.bifrost.domain.entity.Track;
import com.bifrost.domain.repo.AlbumRepository;
import com.bifrost.domain.repo.ArtistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 媒体浏览（《通用功能说明》§9.2：/api/artists|albums|tracks*）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BrowseController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final LibraryQueryService queryService;

    /** 艺术家列表（分页；q 名称过滤、indexLetter 分组过滤） */
    @GetMapping("/artists")
    public ApiResponse<PageResult<ArtistView>> artists(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String indexLetter) {
        String keyword = Strings.trimToNull(q);
        String letter = Strings.trimToNull(indexLetter);
        Map<Long, Long> albumCounts = albumRepository.findAll().stream()
                .filter(a -> a.getArtistId() != null)
                .collect(Collectors.groupingBy(Album::getArtistId, Collectors.counting()));
        List<ArtistView> all = artistRepository.findAllByOrderByNameAsc().stream()
                .filter(a -> keyword == null || a.getName().toLowerCase().contains(keyword.toLowerCase()))
                .filter(a -> letter == null || letter.equalsIgnoreCase(a.getIndexLetter()))
                .map(a -> new ArtistView(a.getId(), a.getName(), a.getIndexLetter(),
                        albumCounts.getOrDefault(a.getId(), 0L), a.getStarredAt(), a.getRating(),
                        a.getPlayCount(), a.getLastPlayed()))
                .toList();
        return ApiResponse.ok(paginate(all, page, size));
    }

    /** 艺术家详情（含专辑列表） */
    @GetMapping("/artists/{id}")
    public ApiResponse<LibraryQueryService.ArtistDetail> artistDetail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.artistDetail(id)
                .orElseThrow(() -> BizException.notFound("艺术家不存在: " + id)));
    }

    /** 专辑列表（分页 + type 排序 + genre/year/artistId 过滤） */
    @GetMapping("/albums")
    public ApiResponse<PageResult<Album>> albums(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer fromYear,
            @RequestParam(required = false) Integer toYear,
            @RequestParam(required = false) Long artistId) {
        AlbumListType listType = parseType(type);
        var query = new LibraryQueryService.AlbumListQuery(listType, size, page, genre,
                fromYear, toYear, null, artistId);
        List<Album> items = queryService.albumList(query);
        long total = queryService.countAlbums(query);
        return ApiResponse.ok(new PageResult<>(total, items));
    }

    /** 专辑详情（含曲目） */
    @GetMapping("/albums/{id}")
    public ApiResponse<LibraryQueryService.AlbumDetail> albumDetail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.albumDetail(id)
                .orElseThrow(() -> BizException.notFound("专辑不存在: " + id)));
    }

    /** 曲目列表（分页；albumId/artistId/q 过滤） */
    @GetMapping("/tracks")
    public ApiResponse<PageResult<Track>> tracks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long albumId,
            @RequestParam(required = false) Long artistId,
            @RequestParam(required = false) String q) {
        List<Track> all = queryService.trackList(albumId, artistId, Strings.trimToNull(q),
                Integer.MAX_VALUE - 1, 0);
        return ApiResponse.ok(paginate(all, page, size));
    }

    /** 曲目详情 */
    @GetMapping("/tracks/{id}")
    public ApiResponse<Track> trackDetail(@PathVariable Long id) {
        return ApiResponse.ok(queryService.trackDetail(id)
                .orElseThrow(() -> BizException.notFound("曲目不存在: " + id)));
    }

    private static AlbumListType parseType(String type) {
        if (type == null || type.isBlank()) {
            return AlbumListType.ALPHABETICAL_BY_NAME;
        }
        // 兼容协议 camelCase（alphabeticalByName）与枚举名两种写法
        AlbumListType protocol = AlbumListType.fromProtocol(type);
        if (protocol != null) {
            return protocol;
        }
        try {
            return AlbumListType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BizException.paramError("不支持的排序类型: " + type);
        }
    }

    private static <T> PageResult<T> paginate(List<T> all, int page, int size) {
        int pageSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        int offset = Math.max(0, page) * pageSize;
        int total = all.size();
        List<T> items = offset >= total ? List.of()
                : new ArrayList<>(all.subList(offset, Math.min(total, offset + pageSize)));
        return new PageResult<>(total, items);
    }
}
