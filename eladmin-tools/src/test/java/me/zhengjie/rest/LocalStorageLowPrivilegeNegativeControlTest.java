/*
 *  Copyright 2026 SovNode (SOV-1322 low privilege negative control battery); see NOTICE.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.rest;

import cn.hutool.jwt.JWTUtil;
import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.service.LocalStorageService;
import me.zhengjie.utils.PageResult;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.SpringBeanHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 板块闭合表 local-storage-picture-upload 与 local-storage-delete 的低权限负向对照。
 * 主体是真实 DispatcherServlet + 真实 @EnableMethodSecurity 代理 + 真实 AuthorityConfig("el")
 * 权限判定 + 真实 JWT 请求头解析链路，服务层全部为 mock。
 * 低权限主体持有闭包表族（LocalStorage/AliPay/Email 三个控制器）内的全部权限，
 * 仅缺少被测路由声明的权限；权限串在运行时从 @PreAuthorize 注解解析，不与具体取值耦合。
 * 修复前（52eb188）两条路由无 @PreAuthorize，用例以“缺少 @PreAuthorize”的授权原因失败；
 * 集成头必须返回 HTTP 403、标准 ApiError 错误体，且服务层（行与文件系统的唯一变更路径）零调用。
 */
@Tag("behavioral")
class LocalStorageLowPrivilegeNegativeControlTest {

    private static final String AUTHORIZATION = "Authorization";
    private static final List<Class<?>> GUARDED_FAMILY = List.of(
            LocalStorageController.class, AliPayController.class, EmailController.class);

    private static AnnotationConfigApplicationContext context;
    private static LocalStorageService localStorageService;
    private static UserDetailsService userDetailsService;
    private static MockMvc mockMvc;

    @BeforeAll
    static void bootSecuredRoutes() {
        localStorageService = mock(LocalStorageService.class);
        userDetailsService = mock(UserDetailsService.class);
        context = new AnnotationConfigApplicationContext(SecuredRoutesConfig.class);
        mockMvc = MockMvcBuilders.standaloneSetup(context.getBean(LocalStorageController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(productionJsonConverter())
                // 与生产 @EnableSpringDataWebSupport 对齐：list 路由的 Pageable 参数解析
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void resetMocksAndIdentity() {
        SecurityContextHolder.clearContext();
        Mockito.reset(userDetailsService, localStorageService);
    }

    @Test
    void pictureUploadIsForbiddenForPrincipalLackingOnlyTheNewPermission() throws Exception {
        String token = lowPrivilegePrincipalLacking("uploadPicture");
        mockMvc.perform(multipart("/api/localStorage/pictures")
                        .file(new MockMultipartFile("file", "badge.png", "image/png", minimalPng()))
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(localStorageService);
    }

    @Test
    void multiDeleteIsForbiddenForPrincipalLackingOnlyTheNewPermission() throws Exception {
        String token = lowPrivilegePrincipalLacking("deleteFile");
        mockMvc.perform(delete("/api/localStorage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[7,8]")
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(localStorageService);
    }

    @Test
    void harnessStillAllowsRoutesThePrincipalIsEntitledTo() throws Exception {
        String token = asAuthenticated("lowpriv-storage-positive-control", familyPermissions());
        when(localStorageService.queryAll(any(), any())).thenReturn(new PageResult<>(List.of(), 0));
        mockMvc.perform(get("/api/localStorage").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static byte[] minimalPng() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }

    /** 低权限主体：持有族内全部权限，仅移除被测路由声明的权限。 */
    private static String lowPrivilegePrincipalLacking(String methodName) {
        Set<String> granted = familyPermissions();
        granted.removeAll(requiredPermissions(methodName));
        return asAuthenticated("lowpriv-" + methodName.toLowerCase(), granted);
    }

    /** 闭包表族的全部权限串：断言“仅缺少新权限”时以此为准。 */
    private static Set<String> familyPermissions() {
        Set<String> all = new LinkedHashSet<>();
        for (Class<?> controller : GUARDED_FAMILY) {
            for (Method method : controller.getDeclaredMethods()) {
                PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                if (preAuthorize != null) {
                    collectQuoted(preAuthorize.value(), all);
                }
            }
        }
        return all;
    }

    /** 被测路由声明的权限串；无 @PreAuthorize 即以授权原因失败（修复前红控制的判据）。 */
    private static Set<String> requiredPermissions(String methodName) {
        Method handler = handler(methodName);
        PreAuthorize preAuthorize = handler.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize, methodName
                + " 缺少 @PreAuthorize：低权限主体当前可直接命中受保护操作，负向对照失败于授权缺失");
        Matcher check = Pattern.compile("@el\\.check\\s*\\(([^)]*)\\)").matcher(preAuthorize.value());
        assertTrue(check.find(), methodName + " 的 @PreAuthorize 必须沿用 @el.check 权限判定");
        Set<String> permissions = new LinkedHashSet<>();
        collectQuoted(check.group(1), permissions);
        assertFalse(permissions.isEmpty(), methodName + " 的 @el.check 未声明权限字符串");
        return permissions;
    }

    private static Method handler(String methodName) {
        for (Method method : LocalStorageController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !method.isSynthetic()) {
                return method;
            }
        }
        throw new IllegalStateException("LocalStorageController 未找到处理器方法 " + methodName);
    }

    private static void collectQuoted(String expression, Set<String> into) {
        Matcher quoted = Pattern.compile("'([^']+)'").matcher(expression);
        while (quoted.find()) {
            into.add(quoted.group(1));
        }
    }

    /** 按生产链路构造当前用户：Bearer JWT(sub) 请求头经 SecurityUtils 解析，UserDetailsService 返回权限集合。 */
    private static String asAuthenticated(String username, Set<String> permissions) {
        SecurityUtils.header = AUTHORIZATION;
        SecurityUtils.tokenStartWith = "Bearer ";
        Set<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        UserDetails userDetails = mock(UserDetails.class);
        Mockito.doReturn(authorities).when(userDetails).getAuthorities();
        Mockito.doReturn(username).when(userDetails).getUsername();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", authorities));
        return JWTUtil.createToken(Map.of("sub", username),
                "sov-1322-test-key".getBytes(StandardCharsets.UTF_8));
    }

    private static FastJsonHttpMessageConverter productionJsonConverter() {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();
        FastJsonConfig config = new FastJsonConfig();
        converter.setFastJsonConfig(config);
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON));
        converter.setDefaultCharset(StandardCharsets.UTF_8);
        return converter;
    }

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
    static class SecuredRoutesConfig {

        @Bean
        SpringBeanHolder springBeanHolder() {
            return new SpringBeanHolder();
        }

        @Bean("el")
        AuthorityConfig authorityConfig() {
            return new AuthorityConfig();
        }

        @Bean
        UserDetailsService productionUserDetailsService() {
            return userDetailsService;
        }

        @Bean
        LocalStorageService productionLocalStorageService() {
            return localStorageService;
        }

        @Bean
        LocalStorageController localStorageController() {
            return new LocalStorageController(productionLocalStorageService());
        }
    }
}
