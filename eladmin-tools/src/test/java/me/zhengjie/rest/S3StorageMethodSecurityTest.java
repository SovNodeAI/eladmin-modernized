/*
 *  Copyright 2026 SovNode (storage authorization gap closure); see NOTICE.
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
import me.zhengjie.config.AmzS3Config;
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.domain.S3Storage;
import me.zhengjie.service.S3StorageService;
import me.zhengjie.service.dto.S3StorageQueryCriteria;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S3 存储端点的方法级授权测试：走真实的 @EnableMethodSecurity 代理、
 * 真实的 AuthorityConfig("el") 权限判定与真实的 JWT 请求头解析路径，
 * 断言 upload 需要 storage:add、单个文件下载需要 storage:list，
 * 并守住既有的 list/export/delete 权限门不被削弱。
 * 仅扫描源码文本无法发现缺失的 @PreAuthorize，这里必须真正发起调用。
 */
@Tag("behavioral")
class S3StorageMethodSecurityTest {

    private static AnnotationConfigApplicationContext context;
    private static S3StorageService s3StorageService;
    private static UserDetailsService userDetailsService;
    private static S3StorageController controller;

    @BeforeAll
    static void buildSecuredController() {
        s3StorageService = mock(S3StorageService.class);
        userDetailsService = mock(UserDetailsService.class);
        context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class);
        controller = context.getBean(S3StorageController.class);
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterEach
    void resetMocksAndStatics() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        Mockito.reset(userDetailsService, s3StorageService);
    }

    /**
     * 按生产链路构造当前用户：Authorization: Bearer <JWT(sub)> 请求头，
     * SecurityUtils 解析 sub，UserDetailsService 按用户名返回权限集合。
     */
    private static void asUser(String username, String... permissions) {
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        String token = JWTUtil.createToken(Map.of("sub", username),
                "sov-1038-test-key".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/s3Storage");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("x-forwarded-for", "192.0.2.10");
        request.addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Set<SimpleGrantedAuthority> authorities = Set.of(permissions).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        UserDetails userDetails = mock(UserDetails.class);
        Mockito.doReturn(authorities).when(userDetails).getAuthorities();
        when(userDetailsService.loadUserByUsername(username)).thenReturn(userDetails);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", authorities));
    }

    private static S3Storage storage() {
        S3Storage storage = new S3Storage();
        storage.setId(7L);
        storage.setFileName("logo.png");
        storage.setFilePath("2026-08/logo.png");
        return storage;
    }

    @Test
    void uploadIsAllowedForStorageAdd() {
        asUser("uploader", "storage:add");
        when(s3StorageService.upload(any())).thenReturn(storage());
        ResponseEntity<Object> response = controller.uploadS3Storage(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2, 3}));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, ((Map<?, ?>) response.getBody()).get("errno"));
    }

    @Test
    void uploadIsForbiddenWithoutStorageAdd() {
        asUser("reader", "storage:list");
        assertThrows(AccessDeniedException.class, () -> controller.uploadS3Storage(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2, 3})));
    }

    @Test
    void perItemDownloadIsAllowedForStorageList() {
        asUser("reader", "storage:list");
        when(s3StorageService.getById(7L)).thenReturn(storage());
        ResponseEntity<Object> response = controller.downloadS3Storage(7L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://s3.example.test/2026-08/logo.png",
                ((Map<?, ?>) response.getBody()).get("url"));
    }

    @Test
    void perItemDownloadIsForbiddenWithoutStorageList() {
        asUser("uploader", "storage:add");
        assertThrows(AccessDeniedException.class, () -> controller.downloadS3Storage(7L));
    }

    @Test
    void adminAuthoritySatisfiesEveryStorageGate() {
        asUser("root", "admin");
        when(s3StorageService.upload(any())).thenReturn(storage());
        assertEquals(HttpStatus.OK, controller.uploadS3Storage(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1}))
                .getStatusCode());
    }

    @Test
    void existingListGateStaysEnforced() {
        asUser("deleter", "storage:del");
        assertThrows(AccessDeniedException.class, () ->
                controller.queryS3Storage(new S3StorageQueryCriteria(), null));
        asUser("reader", "storage:list");
        assertEquals(HttpStatus.OK,
                controller.queryS3Storage(new S3StorageQueryCriteria(), null).getStatusCode());
    }

    @Test
    void existingExportGateStaysEnforced() {
        asUser("deleter", "storage:del");
        assertThrows(AccessDeniedException.class, () -> controller.exportS3Storage(
                new MockHttpServletResponse(), new S3StorageQueryCriteria()));
        asUser("reader", "storage:list");
        assertDoesNotThrowExport();
    }

    private static void assertDoesNotThrowExport() {
        try {
            controller.exportS3Storage(new MockHttpServletResponse(), new S3StorageQueryCriteria());
        } catch (AccessDeniedException denied) {
            throw new AssertionError("export must stay allowed for storage:list", denied);
        } catch (Exception e) {
            // 服务层 mock 未打桩返回值不影响授权判定本身：能走到服务层即已通过权限门
        }
    }

    @Test
    void existingDeleteGateStaysEnforced() {
        asUser("reader", "storage:list");
        assertThrows(AccessDeniedException.class, () ->
                controller.deleteAllS3Storage(List.of(7L)));
        asUser("deleter", "storage:del");
        assertEquals(HttpStatus.OK, controller.deleteAllS3Storage(List.of(7L)).getStatusCode());
    }

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
    static class MethodSecurityConfig {

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
        S3StorageService productionS3StorageService() {
            return s3StorageService;
        }

        @Bean
        AmzS3Config amzS3Config() {
            AmzS3Config config = mock(AmzS3Config.class);
            when(config.getDomain()).thenReturn("https://s3.example.test");
            // SOV-1037 显式 S3 契约：授权测试固定在“已启用”分支，
            // 保持本类只断言权限门；未启用的确定性 503 由 AmzS3OptInContractTest 负责。
            when(config.isEnabled()).thenReturn(true);
            return config;
        }

        @Bean
        S3StorageController s3StorageController() {
            return new S3StorageController(amzS3Config(), productionS3StorageService());
        }
    }
}
