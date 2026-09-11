package com.bifrost.bootstrap.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 请求日志过滤器：把**每一个**进来的请求打一行（方法 + 完整 URL/参数 + 来源 IP），
 * 位置在过滤链最前端（{@link Ordered#HIGHEST_PRECEDENCE}，先于 Spring Security 的两条链、
 * 先于路由匹配与参数校验）。
 *
 * <p>用途：客户端（Subsonic / OPDS / KOSync）某个页面没数据、报"未实现"，但又不知道它到底
 * 调了哪个端点时，看这一行即可拿到**原样 URL**——包括路径是 {@code /rest/xxx} 还是
 * {@code /rest/xxx.view}、{@code c=} 客户端名、{@code f=} 输出格式、以及全部业务参数。
 * 响应完成后再补一行状态码与耗时（Subsonic 协议失败也是 HTTP 200，状态码只反映传输层）。</p>
 *
 * <p><b>凭据打码</b>：Subsonic 把凭据放在查询串里（{@code p} 明文口令、{@code t} 令牌、
 * {@code s} 盐），三者一律记为 {@code ***}；其余参数原样保留。日志落在
 * {@code ${bifrost.data.dir}/logs/bifrost.log}（与业务日志同一文件）。</p>
 *
 * <p>开关 {@code bifrost.request-log.enabled}（默认 true）；生产环境嫌吵可置 false。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "bifrost.request-log", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class RequestLoggingFilter extends OncePerRequestFilter {

    /** 查询串/表单里必须打码的参数：Subsonic 的明文口令（p）、令牌（t）与盐（s） */
    private static final Set<String> SECRET_PARAMS = Set.of("p", "t", "s");

    /** 查询串中的凭据参数（形如 {@code &p=xxx}，保留前导 & 与参数名，仅抹掉值） */
    private static final Pattern SECRET_IN_QUERY = Pattern.compile("(?i)(^|&)(p|t|s)=[^&]*");

    private static final String MASK = "***";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        log.info("--> {} {}{} from {}", request.getMethod(), request.getRequestURI(),
                detail(request), request.getRemoteAddr());
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("<-- {} {} {} ({} ms)", response.getStatus(), request.getMethod(),
                    request.getRequestURI(), (System.nanoTime() - startNanos) / 1_000_000L);
        }
    }

    /**
     * 参数明细：表单 POST（{@code application/x-www-form-urlencoded}）取参数字典，
     * 其余取原始查询串。两者都只打码凭据，不动其它参数。
     */
    private static String detail(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType != null
                && contentType.toLowerCase().startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            return formatParams(request.getParameterMap());
        }
        String query = request.getQueryString();
        return query == null || query.isEmpty() ? "" : "?" + maskQuery(query);
    }

    /** 原始查询串打码（保持客户端发送的原样编码，便于原样复现请求） */
    private static String maskQuery(String query) {
        Matcher matcher = SECRET_IN_QUERY.matcher(query);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(masked,
                    Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + "=" + MASK));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    /** 表单参数打码 */
    private static String formatParams(Map<String, String[]> params) {
        if (params.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("?");
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            if (text.length() > 1) {
                text.append('&');
            }
            text.append(entry.getKey()).append('=');
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                continue;
            }
            if (SECRET_PARAMS.contains(entry.getKey().toLowerCase())) {
                text.append(MASK);
                continue;
            }
            for (int i = 0; i < values.length; i++) {
                if (i > 0) {
                    text.append(',');
                }
                text.append(values[i]);
            }
        }
        return text.toString();
    }
}
