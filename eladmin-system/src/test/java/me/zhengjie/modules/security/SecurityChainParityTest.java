/*
 *  Copyright 2019-2025 Zheng Jie
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
package me.zhengjie.modules.security;

import me.zhengjie.annotation.rest.AnonymousAccess;
import me.zhengjie.annotation.rest.AnonymousDeleteMapping;
import me.zhengjie.annotation.rest.AnonymousGetMapping;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.modules.security.config.SecurityProperties;
import me.zhengjie.modules.security.config.SpringSecurityConfig;
import me.zhengjie.modules.security.security.JwtAccessDeniedHandler;
import me.zhengjie.modules.security.security.JwtAuthenticationEntryPoint;
import me.zhengjie.modules.security.security.TokenProvider;
import me.zhengjie.modules.security.service.OnlineUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无数据库、无 Redis 的安全链路对等性测试：验证 Spring Security 7 迁移后
 * 匿名路由、静态资源、OPTIONS、受保护 GET/PUT 的授权行为与 JSON 失败契约。
 *
 * @author Zheng Jie
 */
@org.junit.jupiter.api.Tag("behavioral")
class SecurityChainParityTest {

    private static final String ENTRY_POINT_BODY_401 =
            "{\"status\":401,\"timestamp\":";
    private static AnnotationConfigWebApplicationContext context;
    private static FilterChainProxy filterChainProxy;

    @BeforeAll
    static void buildChain() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(ParityConfig.class);
        context.register(SpringSecurityConfig.class);
        context.refresh();
        SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
        filterChainProxy = new FilterChainProxy(chain);
    }

    @AfterEach
    void cleanContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse fire(HttpMethod method, String path) throws Exception {
        return fire(method.name(), path);
    }

    private MockHttpServletResponse fire(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setServerName("localhost");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filterChainProxy.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void anonymousLoginIsPermitted() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.POST, "/auth/login").getStatus());
    }

    @Test
    void anonymousLogoutIsPermitted() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.DELETE, "/auth/logout").getStatus());
    }

    @Test
    void anonymousCodeIsPermitted() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/auth/code").getStatus());
    }

    @Test
    void anonymousRootIsPermitted() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/").getStatus());
    }

    @Test
    void staticIndexHtmlKeepsAntSemantics() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/index.html").getStatus());
    }

    @Test
    void staticDocHtmlKeepsAntSemantics() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/doc.html").getStatus());
    }

    @Test
    void staticAppJsKeepsAntSemanticsIncludingNestedPaths() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/static/app.js").getStatus());
    }

    @Test
    void rootLevelAppJsKeepsAntSemantics() throws Exception {
        // 中段 ** 模式（/**/*.js）在根层级（零层目录）也必须命中，
        // 否则根级静态资源会静默变成 401
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/app.js").getStatus());
    }

    @Test
    void rootLevelThemeCssKeepsAntSemantics() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/theme.css").getStatus());
    }

    @Test
    void staticThemeCssKeepsAntSemanticsIncludingNestedPaths() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/static/theme.css").getStatus());
    }

    @Test
    void webSocketInfoIsPermitted() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/webSocket/info").getStatus());
    }

    @Test
    void druidIndexHtmlIsPermitted() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/druid/index.html").getStatus());
    }

    @Test
    void fileRoutesStayAnonymous() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/file/repo.zip").getStatus());
    }

    @Test
    void avatarRoutesStayAnonymous() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/avatar/me.png").getStatus());
    }

    @Test
    void anonymousLimitRouteIsPermitted() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/api/limit").getStatus());
    }

    @Test
    void anonymousAliPayReturnCallbackIsPermitted() throws Exception {
        // 与生产 AliPayController 一致：类前缀 /api/aliPay + @AnonymousGetMapping("/return")
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/api/aliPay/return").getStatus());
    }

    @Test
    void anonymousAliPayNotifyCallbackIsPermittedForEveryMethod() throws Exception {
        // 与生产 AliPayController 一致：裸 @RequestMapping + @AnonymousAccess 落入
        // ALL 匿名桶；注册回归会让支付异步通知（POST）401 且无测试可拦
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.POST, "/api/aliPay/notify").getStatus());
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.GET, "/api/aliPay/notify").getStatus());
    }

    @Test
    void errorDispatchStaysPermittedSoErrorPagesSurvive() throws Exception {
        // Security 7 的 AuthorizationFilter 默认对 ERROR 分发也做鉴权
        // （构造器默认 filterErrorDispatch=true）：容器把 sendError 转成对 /error 的
        // ERROR 分发，若未放行，匿名请求的 404/500 错误页会变成 401 入口点 JSON。
        // 这里按容器的真实行为构造对 /error 的 ERROR 分发请求。
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/error");
        request.setServletPath("/error");
        request.setServerName("localhost");
        request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filterChainProxy.doFilter(request, response, new MockFilterChain());
        assertNotEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus(),
                "ERROR dispatch must stay reachable, got: " + response.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void optionsRequestsArePermittedAnywhere() throws Exception {
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.OPTIONS, "/api/users").getStatus());
        assertEquals(HttpServletResponse.SC_OK, fire(HttpMethod.OPTIONS, "/auth/login").getStatus());
    }

    @Test
    void protectedGetWithoutTokenIsUnauthorizedWithJsonContract() throws Exception {
        MockHttpServletResponse response = fire(HttpMethod.GET, "/api/users");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.startsWith(ENTRY_POINT_BODY_401),
                "entry point JSON shape must be preserved, got: " + body);
    }

    @Test
    void protectedGetWithMalformedBearerTokenHitsDefaultDenyBeforeTokenValidation() throws Exception {
        // TokenProvider 为 mock：这里验证的是链级默认拒绝（任何未通过认证的请求
        // 一律 401），而不是 JWT 解析拒绝本身
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.GET.name(), "/api/users");
        request.setServletPath("/api/users");
        request.addHeader("Authorization", "Bearer not-a-real-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filterChainProxy.doFilter(request, response, new MockFilterChain());
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void protectedPutWithoutTokenIsUnauthorizedEvenWithEmptyAnonymousBucket() throws Exception {
        // 迁移修复点：PUT 匿名桶为空时不得等价于放行所有 PUT 请求
        MockHttpServletResponse response = fire(HttpMethod.PUT, "/api/users/1");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());

        MockHttpServletResponse patch = fire(HttpMethod.PATCH, "/api/users/1");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, patch.getStatus());
    }

    @Test
    void protectedDeleteWithoutTokenIsUnauthorized() throws Exception {
        MockHttpServletResponse response = fire(HttpMethod.DELETE, "/api/users/1");
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class ParityConfig {

        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties properties = new SecurityProperties();
            properties.setHeader("Authorization");
            properties.setTokenStartWith("Bearer");
            properties.setBase64Secret("ZGVmYXVsdC10ZXN0LXNlY3JldC1rZXktbXVzdC1iZS1sb25nLWVub3VnaC0xMjM0NTY3OA==");
            properties.setOnlineKey("online-token:");
            properties.setTokenValidityInSeconds(86400L);
            return properties;
        }

        @Bean
        TokenProvider tokenProvider() {
            return Mockito.mock(TokenProvider.class);
        }

        @Bean
        OnlineUserService onlineUserService() {
            return Mockito.mock(OnlineUserService.class);
        }

        @Bean
        JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
            return new JwtAuthenticationEntryPoint();
        }

        @Bean
        JwtAccessDeniedHandler jwtAccessDeniedHandler() {
            return new JwtAccessDeniedHandler();
        }

        @Bean
        CorsFilter corsFilter() {
            return new CorsFilter(new UrlBasedCorsConfigurationSource());
        }

        @RestController
        static class RootStub {
            @AnonymousGetMapping("/")
            public String index() {
                return "root";
            }
        }

        @RestController
        @RequestMapping("/auth")
        static class AuthStub {
            @AnonymousPostMapping("/login")
            public String login() {
                return "login";
            }

            @AnonymousGetMapping("/code")
            public String code() {
                return "code";
            }

            @AnonymousDeleteMapping("/logout")
            public String logout() {
                return "logout";
            }
        }

        @RestController
        @RequestMapping("/api/limit")
        static class LimitStub {
            @AnonymousGetMapping
            public int limit() {
                return 1;
            }
        }

        @RestController
        @RequestMapping("/api/aliPay")
        static class AliPayStub {

            @AnonymousGetMapping("/return")
            public String payReturn() {
                return "return";
            }

            @RequestMapping("/notify")
            @AnonymousAccess
            public String notifyCallback() {
                return "notify";
            }
        }
    }
}
