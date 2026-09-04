package com.bifrost.core.book;

import com.bifrost.common.util.FileIO;
import com.bifrost.core.book.model.BookResult;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 图书解析器注册表（按扩展名路由；Day-one 包含 EPUB/KEPUB/PDF，{@code doc/m2-book/task/01-图书核心.md} T1.2）。
 *
 * <p>新增格式：实现 {@link BookParser} 并在构造器 {@link #byExtension} 加一行。</p>
 */
@Component
public class BookParserRegistry {

    private final Map<String, BookParser> byExtension;

    public BookParserRegistry(List<BookParser> parsers) {
        this.byExtension = new HashMap<>();
        for (BookParser p : parsers) {
            // 一个 parser 可 supports 多个扩展（如 EpubBookParser 支持 epub + kepub.epub）
            registerIfSupports(p, "epub");
            registerIfSupports(p, "kepub.epub");
            registerIfSupports(p, "pdf");
        }
    }

    private void registerIfSupports(BookParser p, String ext) {
        if (p.supports(ext)) {
            byExtension.put(ext, p);
        }
    }

    /** 路由：扩展名 → parser。 */
    public Optional<BookParser> find(String extension) {
        if (extension == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byExtension.get(extension.toLowerCase()));
    }

    /** 直接按文件扩展名解析；不支持的扩展名 → {@link BookResult#fallback}（{@code parseError=true}）。 */
    public BookResult parse(Path file) {
        String ext = FileIO.extension(file);
        return find(ext)
                .map(p -> p.parse(file))
                .orElseGet(() -> BookResult.fallback(FileIO.fileNameWithoutExtension(file)));
    }
}