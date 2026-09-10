package com.bifrost.adapter.kosync;

import com.bifrost.adapter.kosync.dto.KosyncErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * KOSync 协议错误处理（M3-sync T2.5）。
 *
 * <p>{@code basePackages} 隔离：只处理本模块控制器，<b>绝不</b>返回 {@code /api} 的
 * {@code ApiResponse} 信封（与 {@code OpdsExceptionHandler}、{@code SubsonicExceptionHandler} 同款）。</p>
 *
 * <p>响应 Content-Type 恒为 {@code application/json}：客户端被 patch 过的 JSON 中间件只认
 * {@code application/...json} 形态，回 {@code application/vnd.koreader.v1+json} 会导致它<b>完全不解析响应体</b>。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.bifrost.adapter.kosync")
public class KosyncExceptionHandler {

    @ExceptionHandler(KosyncException.class)
    public ResponseEntity<KosyncErrorResponse> handle(KosyncException e) {
        return json(e.getStatus(), new KosyncErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<KosyncErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        // 官方此处是 Lua 报错 500；给明确错误更友好，KOReader 正常不会发这种请求
        return json(HttpStatus.BAD_REQUEST,
                new KosyncErrorResponse(KosyncConstants.CODE_INVALID_FIELD, "请求体不是合法 JSON"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<KosyncErrorResponse> handleOther(Exception e) {
        log.warn("KOSync 内部错误: {}", e.toString());
        return json(HttpStatus.BAD_GATEWAY,
                new KosyncErrorResponse(KosyncConstants.CODE_INTERNAL, "服务器内部错误"));
    }

    private ResponseEntity<KosyncErrorResponse> json(HttpStatus status, KosyncErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
