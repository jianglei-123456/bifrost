package com.bifrost.adapter.syrinx.subsonic;

import com.bifrost.adapter.syrinx.subsonic.dto.SubsonicResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.config.BifrostProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Subsonic（/rest/**）端点异常处理。
 *
 * <p>与 {@link com.bifrost.api.exception.GlobalExceptionHandler}（仅管理 REST /api/**）隔离：
 * Subsonic 端点异常一律回协议信封（200 + status=failed + 错误码），绝不回管理端 ApiResponse，
 * 避免二进制/流式端点（stream/download/getCoverArt）在响应已带音频 Content-Type 时出现
 * "No converter for [ApiResponse] with preset Content-Type" 而无法回写。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.bifrost.adapter.syrinx.subsonic")
@RequiredArgsConstructor
public class SubsonicExceptionHandler {

    private final SubsonicRenderer renderer;
    private final BifrostProperties properties;

    /** 参数缺失/类型错误 → 协议错误 10。 */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<String> handleBadRequest(HttpServletRequest request, Exception e) {
        return failed(request, 10, "Required parameter is missing: " + e.getMessage());
    }

    /** 业务异常 → 协议错误 0（端点内部多数已就地转 70/10，此处兜底）。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<String> handleBiz(HttpServletRequest request, BizException e) {
        return failed(request, 0, e.getMessage());
    }

    /** 未匹配路径（/rest 下的多余段）→ 未实现。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<String> handleNoResource(HttpServletRequest request, NoResourceFoundException e) {
        return failed(request, 0, "Not implemented");
    }

    /** 未知异常 → 协议错误 0，记录完整堆栈便于定位。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleUnknown(HttpServletRequest request, Exception e) {
        log.error("Subsonic 端点未处理异常: {}", request.getRequestURI(), e);
        return failed(request, 0, "Server error");
    }

    private ResponseEntity<String> failed(HttpServletRequest request, int code, String message) {
        return renderer.render(request, SubsonicResponse.failed(apiVersion(), code, message));
    }

    private String apiVersion() {
        return properties.getSubsonic().getApiVersion();
    }
}
