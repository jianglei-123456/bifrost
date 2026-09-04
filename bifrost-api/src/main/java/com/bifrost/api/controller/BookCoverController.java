package com.bifrost.api.controller;

import com.bifrost.api.response.ApiResponse;
import com.bifrost.common.exception.BizException;
import com.bifrost.core.book.BookCoverService;
import com.bifrost.domain.entity.Book;
import com.bifrost.domain.repo.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * 图书封面上传/删除（M2-book，{@code doc/m2-book/task/03-管理REST.md} T3.5）。
 *
 * <p>POST 接收 multipart file 字段；类型限制 image/jpeg|png|gif；大小限制 5MB。
 * DELETE 移除物理文件 + coverSource=null（扫描再次遇到同文件时自动 EMBEDDED 重抽取）。</p>
 */
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookCoverController {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif");

    private final BookRepository bookRepository;
    private final BookCoverService bookCoverService;

    /** 上传封面（multipart/form-data; file=@...） */
    @PostMapping(value = "/{id}/cover", consumes = "multipart/form-data")
    public ApiResponse<Void> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.paramError("file 不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw BizException.paramError("仅支持 image/jpeg | image/png | image/gif");
        }
        if (file.getSize() > MAX_SIZE) {
            throw BizException.paramError("文件超过 5MB");
        }
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("图书不存在: " + id));
        long written;
        try {
            written = bookCoverService.storeUploadedCover(book, file.getBytes());
        } catch (IOException e) {
            throw BizException.paramError("读取上传文件失败: " + e.getMessage());
        }
        if (written == 0) {
            throw BizException.paramError("封面落盘失败");
        }
        return ApiResponse.ok();
    }

    /** 删除封面（coverSource=null；扫描遇 EMBEDDED 会自动重抽取） */
    @DeleteMapping("/{id}/cover")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("图书不存在: " + id));
        bookCoverService.delete(book);
        return ApiResponse.ok();
    }
}