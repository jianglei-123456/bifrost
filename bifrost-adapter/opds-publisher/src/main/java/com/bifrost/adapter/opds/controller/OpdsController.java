package com.bifrost.adapter.opds.controller;

import com.bifrost.adapter.opds.BookNotFoundException;
import com.bifrost.adapter.opds.OpdsConstants;
import com.bifrost.adapter.opds.service.OpdsBookQueryService;
import com.bifrost.adapter.opds.service.OpdsFeedBuilder;
import com.bifrost.core.book.BookCoverService;
import com.bifrost.domain.entity.Book;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * OPDS 1.2 控制器（M2-book，{@code doc/m2-book/task/02-OPDS发布.md} T2.3-T2.4）。
 *
 * <p>7 个端点：catalog 根、all、recent、search.xml、search、file（带 HTTP Range 206）、cover。
 * 全部基于 {@link OpdsBookQueryService} + {@link OpdsFeedBuilder}，二进制端点用
 * {@link BookCoverService}（封面）和直接流式文件（书体）。</p>
 */
@Slf4j
@RestController
@RequestMapping(OpdsConstants.OPDS_BASE)
@RequiredArgsConstructor
public class OpdsController {

    private final OpdsBookQueryService queryService;
    private final OpdsFeedBuilder feedBuilder;
    private final BookCoverService bookCoverService;

    // ============== Atom feeds ==============

    @GetMapping(value = "/catalog", produces = OpdsConstants.MIME_NAVIGATION)
    public ResponseEntity<String> catalog() {
        return ok(OpdsConstants.MIME_NAVIGATION, feedBuilder.buildCatalogRoot(Instant.now()));
    }

    @GetMapping(value = "/catalog/all", produces = OpdsConstants.MIME_ACQUISITION)
    public ResponseEntity<String> all(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "50") int count) {
        int[] norm = normalize(page, count);
        Page<Book> p = queryService.findAll(norm[0], norm[1]);
        String xml = feedBuilder.buildAcquisitionFeed(
                "urn:bifrost:opds:all:page:" + norm[0],
                "全部图书", p, OpdsConstants.OPDS_BASE + "/catalog/all", false);
        return ok(OpdsConstants.MIME_ACQUISITION, xml);
    }

    @GetMapping(value = "/catalog/recent", produces = OpdsConstants.MIME_ACQUISITION)
    public ResponseEntity<String> recent(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "50") int count) {
        int[] norm = normalize(page, count);
        Page<Book> p = queryService.findRecent(norm[0], norm[1]);
        String xml = feedBuilder.buildAcquisitionFeed(
                "urn:bifrost:opds:recent:page:" + norm[0],
                "最近添加", p, OpdsConstants.OPDS_BASE + "/catalog/recent", true);
        return ok(OpdsConstants.MIME_ACQUISITION, xml);
    }

    @GetMapping(value = "/search.xml", produces = OpdsConstants.MIME_OSDD)
    public ResponseEntity<String> searchDescriptor() {
        return ok(OpdsConstants.MIME_OSDD, feedBuilder.buildOsdd());
    }

    @GetMapping(value = "/search", produces = OpdsConstants.MIME_ACQUISITION)
    public ResponseEntity<String> search(@RequestParam(name = "q", required = false) String q,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "50") int count) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            throw new IllegalArgumentException("q parameter is required");
        }
        int[] norm = normalize(page, count);
        // 转义 LIKE 元字符（% _ \）以避免任意模式匹配
        String safe = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        Page<Book> p = queryService.search(safe, norm[0], norm[1]);
        String xml = feedBuilder.buildAcquisitionFeed(
                "urn:bifrost:opds:search:q:" + urlEncode(keyword) + ":page:" + norm[0],
                "搜索：" + keyword, p, OpdsConstants.OPDS_BASE + "/search", false);
        return ok(OpdsConstants.MIME_ACQUISITION, xml);
    }

    // ============== Binary ==============

    @GetMapping("/catalog/{bookId}/file")
    public ResponseEntity<?> file(@PathVariable Long bookId, HttpServletRequest request) throws IOException {
        Book book = queryService.findById(bookId)
                .filter(Book::getIsAvailable)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        Path file = Path.of(book.getFilePath());
        if (!Files.isRegularFile(file)) {
            throw new BookNotFoundException(bookId);
        }
        long fileSize;
        InputStream in;
        try {
            fileSize = Files.size(file);
            in = Files.newInputStream(file);
        } catch (IOException e) {
            log.warn("OPDS file 打开失败: {}", file);
            throw new BookNotFoundException(bookId);
        }

        String mime = OpdsConstants.mimeForFile(book.getExtension());
        String filename = book.getTitle() + "." + (book.getExtension() == null ? "bin" : book.getExtension());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.add("X-Content-Type-Options", "nosniff");
        headers.setContentType(MediaType.parseMediaType(mime));
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + asciiSafe(filename) + "\"; filename*=UTF-8''" + urlEncode(filename));
        headers.setLastModified(book.getFileLastModified());

        String range = request.getHeader(HttpHeaders.RANGE);
        if (range != null && range.startsWith("bytes=")) {
            long[] r = parseRange(range, fileSize);
            long start = r[0];
            long end = r[1];
            long length = end - start + 1;
            headers.add(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize);
            headers.setContentLength(length);
            in.skipNBytes(start);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers)
                    .body(new InputStreamResource(new LimitedInputStream(in, length)));
        }
        headers.setContentLength(fileSize);
        return ResponseEntity.ok().headers(headers)
                .body(new InputStreamResource(in));
    }

    @GetMapping("/catalog/{bookId}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable Long bookId,
                                       @RequestParam(required = false) Integer size) {
        Book book = queryService.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        if (book.getCoverSource() == null) {
            throw new BookNotFoundException(bookId);
        }
        Optional<byte[]> data = bookCoverService.coverData(bookId, size == null ? 200 : size);
        if (data.isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(data.get());
    }

    // ============== helpers ==============

    private static ResponseEntity<String> ok(String contentType, String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(body);
    }

    private static int[] normalize(int page, int count) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        int c = count;
        if (c < OpdsConstants.MIN_COUNT) {
            c = OpdsConstants.MIN_COUNT;
        }
        if (c > OpdsConstants.MAX_COUNT) {
            c = OpdsConstants.MAX_COUNT;
        }
        return new int[]{page, c};
    }

    /** 解析 Range: bytes=start-end；多 range 不支持，返回单段。 */
    private static long[] parseRange(String range, long fileSize) {
        String spec = range.substring("bytes=".length()).trim();
        int dash = spec.indexOf('-');
        long start;
        long end;
        if (dash == -1) {
            throw new IllegalArgumentException("invalid Range: " + range);
        }
        String startStr = spec.substring(0, dash).trim();
        String endStr = spec.substring(dash + 1).trim();
        if (startStr.isEmpty()) {
            // suffix range: bytes=-N
            long suffix = Long.parseLong(endStr);
            if (suffix <= 0 || suffix > fileSize) {
                throw new IllegalArgumentException("invalid Range: " + range);
            }
            start = fileSize - suffix;
            end = fileSize - 1;
        } else {
            start = Long.parseLong(startStr);
            end = endStr.isEmpty() ? fileSize - 1 : Long.parseLong(endStr);
        }
        if (start < 0 || end >= fileSize || start > end) {
            throw new IllegalArgumentException("out of range: " + range);
        }
        return new long[]{start, end};
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String asciiSafe(String s) {
        // 简单 ASCII 化兜底（避免 Tomcat 丢弃非 ASCII 头值）
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 128) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    /** 限制读取长度的输入流（Range 响应用）。 */
    private static final class LimitedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        LimitedInputStream(InputStream in, long length) {
            this.in = in;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = in.read(b, off, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}