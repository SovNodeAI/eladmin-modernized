/*
 *  Copyright 2026 SovNode (email authorization and secret redaction, SOV-1319); see NOTICE.
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
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.domain.EmailConfig;
import me.zhengjie.domain.vo.EmailVo;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.service.EmailService;
import me.zhengjie.service.dto.EmailConfigRedactedDto;
import me.zhengjie.service.dto.EmailConfigUpdateDto;
import me.zhengjie.service.dto.SecretMask;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.SpringBeanHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件端点的方法级授权与秘密脱敏测试：走真实的 @EnableMethodSecurity 代理与
 * 真实的 AuthorityConfig("el") 权限判定，断言配置读需要 email:list、配置写需要
 * email:edit、发送需要 email:send；GET /api/email 的响应只以掩码哨兵占位 pass、
 * 绝不序列化真实邮箱密码，更新入参密码留空或回传哨兵时沿用已存密码而不置空，
 * 哨兵在任何更新路径下都不会被持久化为真实密码。
 * 对应 SOV-1319 / 板报 SEC-003。
 */
@Tag("behavioral")
class EmailMethodSecurityTest {

    private static final String STORED_PASS = "SOV1319-stored-email-pass";

    private static AnnotationConfigApplicationContext context;
    private static EmailService emailService;
    private static UserDetailsService userDetailsService;
    private static EmailController controller;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void buildSecuredController() {
        emailService = mock(EmailService.class);
        userDetailsService = mock(UserDetailsService.class);
        context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class);
        controller = context.getBean(EmailController.class);
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
        Mockito.reset(userDetailsService, emailService);
    }

    private static void asUser(String username, String... permissions) {
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        String token = JWTUtil.createToken(Map.of("sub", username),
                "sov-1319-test-key".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/email");
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

    private static EmailConfig storedConfig() {
        EmailConfig config = new EmailConfig();
        config.setId(1L);
        config.setHost("smtp.example.test");
        config.setPort("465");
        config.setUser("sender@example.test");
        config.setPass(STORED_PASS);
        config.setFromUser("sender@example.test");
        return config;
    }

    private static EmailConfigUpdateDto updateDto(String pass) {
        EmailConfigUpdateDto dto = new EmailConfigUpdateDto();
        dto.setHost("smtp.example.test");
        dto.setPort("465");
        dto.setUser("sender@example.test");
        dto.setPass(pass);
        dto.setFromUser("sender@example.test");
        return dto;
    }

    private static EmailVo emailVo() {
        return new EmailVo(List.of("recipient@example.test"), "SOV-1319 主题", "SOV-1319 内容");
    }

    @Test
    void configReadIsAllowedForEmailList() {
        asUser("reader", "email:list");
        when(emailService.find()).thenReturn(storedConfig());
        ResponseEntity<EmailConfigRedactedDto> response = controller.queryEmailConfig();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("smtp.example.test", response.getBody().getHost());
    }

    @Test
    void configReadIsForbiddenWithoutEmailList() {
        asUser("lowpriv", "storage:list");
        assertThrows(AccessDeniedException.class, () -> controller.queryEmailConfig());
    }

    @Test
    void configReadNeverSerializesStoredPass() throws Exception {
        asUser("admin", "admin");
        when(emailService.find()).thenReturn(storedConfig());
        EmailConfigRedactedDto body = controller.queryEmailConfig().getBody();
        assertEquals(SecretMask.SENTINEL, body.getPass());
        String json = objectMapper.writeValueAsString(body);
        assertFalse(json.contains(STORED_PASS), "脱敏读视图不得回显已存密码: " + json);
        assertTrue(json.contains(SecretMask.SENTINEL));
        assertTrue(json.contains("smtp.example.test"));
    }

    @Test
    void configReadWithNoStoredConfigLeavesSecretNull() {
        asUser("admin", "admin");
        when(emailService.find()).thenReturn(new EmailConfig());
        EmailConfigRedactedDto body = controller.queryEmailConfig().getBody();
        assertNull(body.getPass(), "无已存配置时不得伪造掩码，保持首次录入表单行为");
    }

    @Test
    void configUpdateIsForbiddenWithoutEmailEdit() throws Exception {
        asUser("reader", "email:list");
        assertThrows(AccessDeniedException.class,
                () -> controller.updateEmailConfig(updateDto("")));
        verify(emailService, never()).config(any(), any());
    }

    @Test
    void blankPassOnUpdateRetainsStoredSecret() throws Exception {
        asUser("editor", "email:edit");
        when(emailService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK, controller.updateEmailConfig(updateDto("")).getStatusCode());
        ArgumentCaptor<EmailConfig> captor = ArgumentCaptor.forClass(EmailConfig.class);
        verify(emailService).config(captor.capture(), any());
        assertEquals(STORED_PASS, captor.getValue().getPass());
        assertEquals(1L, captor.getValue().getId());
    }

    @Test
    void nullPassOnUpdateRetainsStoredSecret() throws Exception {
        asUser("editor", "email:edit");
        when(emailService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK, controller.updateEmailConfig(updateDto(null)).getStatusCode());
        ArgumentCaptor<EmailConfig> captor = ArgumentCaptor.forClass(EmailConfig.class);
        verify(emailService).config(captor.capture(), any());
        assertEquals(STORED_PASS, captor.getValue().getPass());
    }

    @Test
    void newPassReplacesStoredSecret() throws Exception {
        asUser("editor", "email:edit");
        when(emailService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK,
                controller.updateEmailConfig(updateDto("rotated-pass-2026")).getStatusCode());
        ArgumentCaptor<EmailConfig> captor = ArgumentCaptor.forClass(EmailConfig.class);
        verify(emailService).config(captor.capture(), any());
        assertEquals("rotated-pass-2026", captor.getValue().getPass());
    }

    @Test
    void sentinelPassOnUpdateRetainsStoredSecret() throws Exception {
        asUser("editor", "email:edit");
        when(emailService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK,
                controller.updateEmailConfig(updateDto(SecretMask.SENTINEL)).getStatusCode());
        ArgumentCaptor<EmailConfig> captor = ArgumentCaptor.forClass(EmailConfig.class);
        verify(emailService).config(captor.capture(), any());
        assertEquals(STORED_PASS, captor.getValue().getPass());
    }

    @Test
    void storedSecretNeverEqualsSentinelAfterAnyUpdatePath() throws Exception {
        asUser("editor", "email:edit");
        for (String submitted : new String[] {null, "", "  ", SecretMask.SENTINEL, "rotated-pass-2026"}) {
            Mockito.reset(emailService);
            when(emailService.find()).thenReturn(storedConfig());
            assertEquals(HttpStatus.OK,
                    controller.updateEmailConfig(updateDto(submitted)).getStatusCode(), "path: " + submitted);
            ArgumentCaptor<EmailConfig> captor = ArgumentCaptor.forClass(EmailConfig.class);
            verify(emailService).config(captor.capture(), any());
            assertNotEquals(SecretMask.SENTINEL, captor.getValue().getPass(), "path: " + submitted);
            if (SecretMask.isRetained(submitted)) {
                assertEquals(STORED_PASS, captor.getValue().getPass(), "path: " + submitted);
            } else {
                assertEquals(submitted, captor.getValue().getPass(), "path: " + submitted);
            }
        }
    }

    @Test
    void firstTimeConfigWithoutPassIsRejected() throws Exception {
        asUser("editor", "email:edit");
        when(emailService.find()).thenReturn(new EmailConfig());
        assertThrows(BadRequestException.class,
                () -> controller.updateEmailConfig(updateDto("")));
        assertThrows(BadRequestException.class,
                () -> controller.updateEmailConfig(updateDto(SecretMask.SENTINEL)));
        verify(emailService, never()).config(any(), any());
    }

    @Test
    void sendIsAllowedForEmailSend() {
        asUser("sender", "email:send");
        when(emailService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK, controller.sendEmail(emailVo()).getStatusCode());
        verify(emailService).send(any(), any());
    }

    @Test
    void sendIsForbiddenWithoutEmailSend() {
        asUser("editor", "email:edit");
        assertThrows(AccessDeniedException.class, () -> controller.sendEmail(emailVo()));
        verify(emailService, never()).send(any(), any());
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
        EmailService productionEmailService() {
            return emailService;
        }

        @Bean
        EmailController emailController() {
            return new EmailController(productionEmailService());
        }
    }
}
