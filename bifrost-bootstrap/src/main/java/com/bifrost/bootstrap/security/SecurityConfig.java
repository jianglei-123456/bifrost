package com.bifrost.bootstrap.security;

import com.bifrost.core.config.BifrostProperties;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Spring Security 配置（ADR-0003，Q5-B/Q21）。
 *
 * <p>双过滤器链：/api/**（HTTP Basic 或 t/s 令牌，失败 401 + 信封 code=1002）；
 * /rest/**（Subsonic u/t/s/p 认证，失败 200 + failed + 协议错误码）。
 * 无状态会话、禁用 CSRF；认证拒绝由自定义过滤器内联完成（Subsonic 协议要求 200 非 401）。
 * 两条链均启用 CORS（bifrost.cors.allowed-origins，默认 *），供 Web 客户端（含 Vue Dashboard）跨域调用。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationService authenticationService;

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .cors(withDefaults())
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
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new SubsonicAuthenticationFilter(authenticationService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** CORS 来源策略：来源/方法/头来自 bifrost.cors.allowed-origins（空=禁用），无凭据模式（无状态认证）。 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(BifrostProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(properties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
