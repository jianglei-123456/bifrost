package com.bifrost.bootstrap.security;

import com.bifrost.adapter.kosync.KosyncConstants;
import com.bifrost.core.book.sync.SyncAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * KOSync 安全链（M3-sync T2.2）——<b>必做项，不是可选优化</b>。
 *
 * <p>项目当前只有 {@code /api/**}、{@code /rest/**}、{@code /opds/**} 三条链，各自用
 * {@code securityMatcher} 圈定前缀；Spring Boot 的默认链因为已存在 {@code SecurityFilterChain} Bean
 * 而被抑制。后果：<b>未被任何 securityMatcher 命中的新前缀是完全不设防的</b>——
 * 所以 {@code /users/**}、{@code /syncs/**} 必须显式声明本链。</p>
 *
 * <p>鉴权在 {@link KosyncAuthenticationFilter} 内联完成（与 {@code /api}、{@code /rest} 同款），
 * 因此这里 {@code anyRequest().permitAll()} 表示"授权交给过滤器"，<b>不是</b>匿名放行。</p>
 *
 * <p>开关与 {@code KosyncController} 同为 {@code bifrost.kosync.enabled}：两者必须同生同死，
 * 否则会出现"链没建但端点还在"的裸奔。集成测试里有一条"关闭开关 → 端点 404"的回归用例。</p>
 */
@Configuration
@ConditionalOnProperty(name = "bifrost.kosync.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class KosyncSecurityConfig {

    private final SyncAccountService syncAccountService;

    @Bean
    public SecurityFilterChain kosyncSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(KosyncConstants.SECURITY_PATTERNS)
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new KosyncAuthenticationFilter(syncAccountService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
