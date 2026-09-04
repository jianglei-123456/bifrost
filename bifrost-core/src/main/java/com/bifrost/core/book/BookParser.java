package com.bifrost.core.book;

import com.bifrost.core.book.model.BookResult;

import java.nio.file.Path;

/**
 * 图书元数据解析器（按容器格式分发；只读、绝不写回源文件）。
 *
 * <p>约定：解析异常必须捕获并返回 {@link BookResult#fallback}，
 * 永不抛异常（与 {@code AudioTagParser} 同模式），由注册表统一路由。</p>
 */
public interface BookParser {

    /** 是否支持该扩展名（{@code epub} / {@code kepub.epub} / {@code pdf} …）。 */
    boolean supports(String extension);

    /**
     * 解析文件元数据。
     *
     * @param file 文件绝对路径
     * @return 归一化结果（永不 null）
     */
    BookResult parse(Path file);
}