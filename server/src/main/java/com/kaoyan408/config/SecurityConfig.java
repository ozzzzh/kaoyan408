package com.kaoyan408.config;

import com.kaoyan408.core.ApiAccessDeniedHandler;
import com.kaoyan408.core.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 无状态鉴权：内容类 GET 接口匿名可读，做题与个人数据需要 Bearer token。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ApiAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ApiAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   @Value("${kaoyan.cors.origins:}") String corsOrigins)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsOrigins)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        // 匿名访问受保护资源走 EntryPoint，已登录但无权限走 AccessDeniedHandler，
                        // 两者都要返回统一响应体。
                        .authenticationEntryPoint(accessDeniedHandler)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 放行错误页，让未匹配路径能走到 MVC 并返回统一的 404 文案
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/exams", "/api/v1/exams/**",
                                "/api/v1/questions/**",
                                "/api/v1/knowledge", "/api/v1/knowledge/**",
                                "/api/v1/stats/dash", "/api/v1/stats/points").permitAll()
                        // 只保护「明确的写操作与个人数据」端点，其余交给 MVC 返回统一 404 文案
                        .requestMatchers("/api/v1/user/**").authenticated()
                        // 背诵卡片是用户私有数据，全部需要登录
                        .requestMatchers("/api/v1/memory/**").authenticated()
                        // 模拟考试：只有「历史成绩」需要登录。开考/答题/交卷允许游客，
                        // 由 MockExamService 走内存态，交卷只返回成绩不落库。
                        .requestMatchers(HttpMethod.GET, "/api/v1/mock/history").authenticated()
                        // 答题接口对游客开放：服务端仍会判分并返回解析，只是不写任何记录。
                        // 之前要求登录，导致游客能打开刷题页却答不出结果。
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 白名单。
     *
     * 原实现只允许 localhost/127.0.0.1，导致部署到内网地址（如
     * http://10.0.219.241:17073）后，浏览器发起的 POST 会带 Origin 头，
     * Spring Security 判定为跨域并直接返回 403——命令行 curl 不带 Origin
     * 却正常，所以这个坑只在浏览器里出现，很容易被误判成"注册接口坏了"。
     *
     * 现在分两部分：
     *   1. 固定允许本地开发的各种端口（Vite 5173、DSH 面板 19387 等）；
     *   2. 通过 KAOYAN_CORS_ORIGINS 环境变量追加部署地址，
     *      用逗号分隔，形如 http://10.0.219.241:17073。
     *      不在代码里写死具体 IP，避免每换一台机器就要改代码重新构建。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${kaoyan.cors.origins:}") String extraOrigins) {
        List<String> patterns = new ArrayList<>(List.of(
                "http://localhost:*", "http://127.0.0.1:*"));
        if (extraOrigins != null && !extraOrigins.isBlank()) {
            for (String origin : extraOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty()) {
                    patterns.add(trimmed);
                }
            }
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}