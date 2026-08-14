package com.bifrost.bootstrap.security;

import com.bifrost.core.security.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置（ADR-0003，Q5-B/Q21）。
 *
 * <p>双过滤器链：/api/**（HTTP Basic 或 t/s 令牌，失败 401 + 信封 code=1002）；
 * /rest/**（Subsonic u/t/s/p 认证，失败 200 + failed + 协议错误码）。
 * 无状态会话、禁用 CSRF；认证拒绝由自定义过滤器内联完成（Subsonic 协议要求 200 非 401）。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationService authenticationService;

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new ApiAuthenticationFilter(authenticationService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public SecurityFilterChain subsonicSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/rest/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new SubsonicAuthenticationFilter(authenticationService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
