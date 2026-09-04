package com.bifrost.adapter.opds;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * OPDS 错误处理（M2-book，T2.5）。
 *
 * <p>OPDS 错误响应**不是 Atom**：客户端不强求格式，简单 XML 即可。
 * 404/400/500 用 {@code <error><code>...</code><message>...</message></error>}。</p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.bifrost.adapter.opds")
public class OpdsExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<String> handleNotFound(BookNotFoundException e) {
        return xmlError(HttpStatus.NOT_FOUND, 404, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return xmlError(HttpStatus.BAD_REQUEST, 400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAny(Exception e, HttpServletResponse response) {
        log.warn("OPDS 端点未处理异常", e);
        return xmlError(HttpStatus.INTERNAL_SERVER_ERROR, 500, "internal error");
    }

    private static ResponseEntity<String> xmlError(HttpStatus status, int code, String message) {
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<error><code>" + code + "</code><message>"
                + (message == null ? "" : message.replace("<", "&lt;").replace(">", "&gt;").replace("&", "&amp;"))
                + "</message></error>";
        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("application/xml;charset=UTF-8"))
                .body(body);
    }
}