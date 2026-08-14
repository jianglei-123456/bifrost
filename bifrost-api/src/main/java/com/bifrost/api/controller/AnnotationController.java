package com.bifrost.api.controller;

import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.audio.service.AnnotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标注（收藏/评分）（《通用功能说明》§9.2：/api/starred、/api/rating）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnnotationController {

    /** 标注请求体 {type: track|album|artist, id} */
    public record AnnotationRequest(String type, Long id) {
    }

    /** 评分请求体 {type, id, rating 1–5} */
    public record RatingRequest(String type, Long id, Integer rating) {
    }

    private final AnnotationService annotationService;

    /** 收藏（type=track/album/artist） */
    @PostMapping("/starred")
    public ApiResponse<Void> star(@RequestBody AnnotationRequest request) {
        require(request);
        annotationService.star(request.type(), request.id());
        return ApiResponse.ok();
    }

    /** 取消收藏 */
    @DeleteMapping("/starred")
    public ApiResponse<Void> unstar(@RequestBody AnnotationRequest request) {
        require(request);
        annotationService.unstar(request.type(), request.id());
        return ApiResponse.ok();
    }

    /** 评分 1–5（0=取消） */
    @PutMapping("/rating")
    public ApiResponse<Void> rate(@RequestBody RatingRequest request) {
        require(request);
        if (request.rating() == null) {
            throw BizException.paramError("rating 不能为空");
        }
        annotationService.rate(request.type(), request.id(), request.rating());
        return ApiResponse.ok();
    }

    private static void require(AnnotationRequest request) {
        if (request.type() == null || request.id() == null) {
            throw BizException.paramError("type 与 id 不能为空");
        }
    }

    private static void require(RatingRequest request) {
        if (request.type() == null || request.id() == null) {
            throw BizException.paramError("type 与 id 不能为空");
        }
    }
}
