package com.bifrost.bootstrap.security;

import com.bifrost.core.config.BifrostProperties;
import com.bifrost.core.security.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OPDS 1.2 鉴权（M2-book，Q6-C，T2.6）。
 *
 * <p>仅当 {@code bifrost.opds.require-auth=true} 时挂载；未启用时 {@code /opds/**} 由
 * 默认无认证过滤器放行（匿名可访问）。</p>
 *
 * <p>启用时复用 {@link AuthenticationService}（与 {@code /api/**} 同账密），HTTP Basic 协议；
 * 未认证返回 {@code 401 + WWW-Authenticate: Basic realm="Bifrost OPDS"}，KOReader 输账密即可。</p>
 */
@Configuration
@ConditionalOnProperty(name = "bifrost.opds.require-auth", havingValue = "true")
@RequiredArgsConstructor
public class OpdsSecurityConfig {

    private final AuthenticationService authenticationService;
    private final BifrostProperties properties;

    @Bean
    public SecurityFilterChain opdsSecurityFilterChain(HttpSecurity http) throws Exception {
        // 复用 /api/** 的 Basic 认证过滤器
        http.securityMatcher("/opds/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(new ApiAuthenticationFilter(authenticationService),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}