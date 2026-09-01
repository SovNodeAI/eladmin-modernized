/*
 *  Copyright 2026 SovNode (storage authorization gap closure, SOV-1319); see NOTICE.
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
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.domain.LocalStorage;
import me.zhengjie.service.LocalStorageService;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本地存储端点的方法级授权测试：走真实的 @EnableMethodSecurity 代理与真实的
 * AuthorityConfig("el") 权限判定，断言 uploadPicture 需要 storage:add、
 * deleteFile 需要 storage:del，并守住既有权限门不被削弱。
 * 对应 SOV-1319 / 板报 SEC-001。
 */
@Tag("behavioral")
class LocalStorageMethodSecurityTest {

    private static AnnotationConfigApplicationContext context;
    private static LocalStorageService localStorageService;
    private static UserDetailsService userDetailsService;
    private static LocalStorageController controller;

    @BeforeAll
    static void buildSecuredController() {
        localStorageService = mock(LocalStorageService.class);
        userDetailsService = mock(UserDetailsService.class);
        context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class);
        controller = context.getBean(LocalStorageController.class);
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
        Mockito.reset(userDetailsService, localStorageService);
    }

    private static void asUser(String username, String... permissions) {
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        String token = JWTUtil.createToken(Map.of("sub", username),
                "sov-1319-test-key".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/localStorage");
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

    @Test
    void uploadPictureIsAllowedForStorageAdd() {
        asUser("uploader", "storage:add");
        when(localStorageService.create(any(), any())).thenReturn(new LocalStorage());
        ResponseEntity<LocalStorage> response = controller.uploadPicture(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2, 3}));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void uploadPictureIsForbiddenWithoutStorageAdd() {
        asUser("reader", "storage:list");
        assertThrows(AccessDeniedException.class, () -> controller.uploadPicture(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2, 3})));
        verify(localStorageService, never()).create(any(), any());
    }

    @Test
    void deleteFileIsAllowedForStorageDel() {
        asUser("deleter", "storage:del");
        ResponseEntity<Object> response = controller.deleteFile(new Long[] {1L, 2L});
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(localStorageService).deleteAll(new Long[] {1L, 2L});
    }

    @Test
    void deleteFileIsForbiddenWithoutStorageDel() {
        asUser("uploader", "storage:add");
        assertThrows(AccessDeniedException.class,
                () -> controller.deleteFile(new Long[] {1L}));
        verify(localStorageService, never()).deleteAll(any());
    }

    @Test
    void existingCreateGateStaysEnforced() {
        asUser("deleter", "storage:del");
        assertThrows(AccessDeniedException.class, () ->
                controller.createFile("logo.png",
                        new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1})));
        verify(localStorageService, never()).create(any(), any());
    }

    @Test
    void existingUpdateGateStaysEnforced() {
        asUser("uploader", "storage:add");
        assertThrows(AccessDeniedException.class, () ->
                controller.updateFile(new LocalStorage()));
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
        LocalStorageService productionLocalStorageService() {
            return localStorageService;
        }

        @Bean
        LocalStorageController localStorageController() {
            return new LocalStorageController(productionLocalStorageService());
        }
    }
}
