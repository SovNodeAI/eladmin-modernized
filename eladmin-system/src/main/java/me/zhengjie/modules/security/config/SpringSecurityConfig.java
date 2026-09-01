/*
 *  Copyright 2019-2025 Zheng Jie
 *  Modified by SovNode in 2026 (Spring Boot 4.1 modernization); see NOTICE.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.security.config;

import lombok.RequiredArgsConstructor;
import me.zhengjie.modules.security.security.*;
import me.zhengjie.modules.security.service.OnlineUserService;
import me.zhengjie.utils.AnonTagUtils;
import me.zhengjie.utils.enums.RequestMethodEnum;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.CorsFilter;
import java.util.*;

/**
 * @author Zheng Jie
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
public class SpringSecurityConfig {

    private final TokenProvider tokenProvider;
    private final CorsFilter corsFilter;
    private final JwtAuthenticationEntryPoint authenticationErrorHandler;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final ApplicationContext applicationContext;
    private final SecurityProperties properties;
    private final OnlineUserService onlineUserService;

    @Bean
    GrantedAuthorityDefaults grantedAuthorityDefaults() {
        // 去除 ROLE_ 前缀
        return new GrantedAuthorityDefaults("");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 密码加密方式
        return new BCryptPasswordEncoder();
    }

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // 获取匿名标记
        Map<String, Set<String>> anonymousUrls = AnonTagUtils.getAnonymousUrl(applicationContext);
        return httpSecurity
                // 禁用 CSRF
                .csrf(AbstractHttpConfigurer::disable)
                .addFilter(corsFilter)
                // 授权异常
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(authenticationErrorHandler)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                // 防止iframe 造成跨域
                .headers(h -> h.frameOptions(fo -> fo.disable()))
                // 不创建会话
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> {
                    // Security 7 的 AuthorizationFilter 默认对 ERROR 分发也做鉴权，
                    // 未放行时匿名请求触发 sendError（如 404）会拿到 401 入口点 JSON
                    // 而不是错误页。这里恢复 Security 5/6 时代的有效行为：ERROR 分发放行。
                    authz.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll();
                    // 静态资源等等：PathPattern 在 Framework 7 已支持嵌套 **（含零层目录），语义与原 Ant 匹配一致
                    authz.requestMatchers(
                            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/*.html"),
                            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/**/*.html"),
                            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/**/*.css"),
                            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/**/*.js"),
                            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/webSocket/**")).permitAll();
                    // OpenAPI 文档
                    authz.requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                            "/swagger-ui.html", "/doc.html", "/webjars/**").permitAll();
                    // 文件
                    authz.requestMatchers("/avatar/**", "/file/**").permitAll();
                    // 阿里巴巴 druid
                    authz.requestMatchers("/druid/**").permitAll();
                    // 放行OPTIONS请求
                    authz.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // 自定义匿名访问所有url放行：允许匿名和带Token访问，细腻化到每个 Request 类型
                    anon(authz, HttpMethod.GET, anonymousUrls.get(RequestMethodEnum.GET.getType()));
                    anon(authz, HttpMethod.POST, anonymousUrls.get(RequestMethodEnum.POST.getType()));
                    anon(authz, HttpMethod.PUT, anonymousUrls.get(RequestMethodEnum.PUT.getType()));
                    anon(authz, HttpMethod.PATCH, anonymousUrls.get(RequestMethodEnum.PATCH.getType()));
                    anon(authz, HttpMethod.DELETE, anonymousUrls.get(RequestMethodEnum.DELETE.getType()));
                    // 所有类型的接口都放行
                    authz.requestMatchers(anonymousUrls.get(RequestMethodEnum.ALL.getType())
                            .toArray(String[]::new)).permitAll();
                    // 所有请求都需要认证
                    authz.anyRequest().authenticated();
                })
                .with(securityConfigurerAdapter(), configurer -> { })
                .build();
    }

    /**
     * 空集合防御：requestMatchers(method, 空数组) 在 Spring Security 语义上并不会
     * 放行整个方法，这里只是防御性跳过注册，使每个匿名桶的语义保持显式。
     */
    private void anon(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authz,
                      HttpMethod method, Set<String> patterns) {
        if (patterns != null && !patterns.isEmpty()) {
            authz.requestMatchers(method, patterns.toArray(String[]::new)).permitAll();
        }
    }

    private TokenConfigurer securityConfigurerAdapter() {
        return new TokenConfigurer(tokenProvider, properties, onlineUserService);
    }
}
