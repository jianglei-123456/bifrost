package com.bifrost.api.controller;

import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.common.util.Strings;
import com.bifrost.core.audio.service.LibraryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 全局搜索（《通用功能说明》§9.2：/api/search?q=）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {

    private final LibraryQueryService queryService;

    /** 搜索艺术家/专辑/曲目（三类各返回前 size 条）。 */
    @GetMapping("/search")
    public ApiResponse<LibraryQueryService.SearchResult> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "20") int size) {
        if (Strings.isBlank(q)) {
            throw BizException.paramError("搜索关键字不能为空");
        }
        return ApiResponse.ok(queryService.search(Strings.trimToNull(q),
                Math.max(1, size), Math.max(1, size), Math.max(1, size), null));
    }
}
