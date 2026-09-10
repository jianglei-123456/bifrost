package com.bifrost.api.exception;

import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.constant.ErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 上传体积超限的兜底（1.0.0 补）。
 *
 * <p>为什么不能放进 {@link GlobalExceptionHandler}：multipart 超限在
 * <b>DispatcherServlet 的解析阶段</b>就抛（{@code checkMultipart}），那时还没有解析出 handler，
 * 而带 {@code basePackages} 限定的 {@code @ControllerAdvice} 是靠 controller 类型选中的——
 * handler 为 null 时它选不中，异常会落到 {@code DefaultHandlerExceptionResolver} →
 * 413 + Spring 白板 JSON（没有 {@code {code,message,data}} 信封，管理端的错误提示只能显示
 * "Request failed with status code 413"）。实测（真容器 + 6MB 上传）就是这样。</p>
 *
 * <p>这条 advice <b>不带包限定</b>，因此能接住解析期异常；影响面可控——它只认这一种异常，
 * 而 multipart 端点目前只有 {@code POST /api/books/{id}/cover}。
 * 状态码用 413（语义正确），业务码沿用 {@code 1000} 参数错误。</p>
 */
@Slf4j
@RestControllerAdvice
public class UploadSizeExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        log.debug("上传超限: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCodes.PARAM_ERROR, "上传文件过大（封面最大 5MB）"));
    }
}
