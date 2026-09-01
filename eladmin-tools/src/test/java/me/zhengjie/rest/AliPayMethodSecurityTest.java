/*
 *  Copyright 2026 SovNode (alipay authorization and secret redaction, SOV-1319); see NOTICE.
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
import me.zhengjie.domain.AlipayConfig;
import me.zhengjie.domain.vo.TradeVo;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.service.AliPayService;
import me.zhengjie.service.dto.AlipayConfigRedactedDto;
import me.zhengjie.service.dto.AlipayConfigUpdateDto;
import me.zhengjie.service.dto.SecretMask;
import me.zhengjie.utils.AlipayUtils;
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
 * 支付宝端点的方法级授权与秘密脱敏测试：走真实的 @EnableMethodSecurity 代理与
 * 真实的 AuthorityConfig("el") 权限判定，断言配置读需要 aliPay:list、配置写需要
 * aliPay:edit、支付需要 aliPay:pay；GET /api/aliPay 的响应只以掩码哨兵占位 privateKey、
 * 绝不序列化真实商户私钥，更新入参私钥留空或回传哨兵时沿用已存私钥而不置空，
 * 哨兵在任何更新路径下都不会被持久化为真实私钥。
 * 对应 SOV-1319 / 板报 SEC-002。
 */
@Tag("behavioral")
class AliPayMethodSecurityTest {

    private static final String STORED_PRIVATE_KEY = "SOV1319-stored-merchant-private-key";

    private static AnnotationConfigApplicationContext context;
    private static AliPayService alipayService;
    private static UserDetailsService userDetailsService;
    private static AliPayController controller;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void buildSecuredController() {
        alipayService = mock(AliPayService.class);
        userDetailsService = mock(UserDetailsService.class);
        context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class);
        controller = context.getBean(AliPayController.class);
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
        Mockito.reset(userDetailsService, alipayService);
    }

    private static void asUser(String username, String... permissions) {
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        String token = JWTUtil.createToken(Map.of("sub", username),
                "sov-1319-test-key".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/aliPay");
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

    private static AlipayConfig storedConfig() {
        AlipayConfig config = new AlipayConfig();
        config.setId(1L);
        config.setAppId("appid-2026083100012345");
        config.setPrivateKey(STORED_PRIVATE_KEY);
        config.setPublicKey("alipay-public-key");
        config.setNotifyUrl("https://example.test/notify");
        config.setReturnUrl("https://example.test/return");
        config.setSysServiceProviderId("provider-001");
        return config;
    }

    private static AlipayConfigUpdateDto updateDto(String privateKey) {
        AlipayConfigUpdateDto dto = new AlipayConfigUpdateDto();
        dto.setAppId("appid-2026083100012345");
        dto.setPrivateKey(privateKey);
        dto.setPublicKey("alipay-public-key");
        dto.setNotifyUrl("https://example.test/notify");
        dto.setReturnUrl("https://example.test/return");
        dto.setSysServiceProviderId("provider-001");
        return dto;
    }

    private static TradeVo trade() {
        TradeVo trade = new TradeVo();
        trade.setSubject("SOV-1319 测试商品");
        trade.setBody("SOV-1319 测试商品描述");
        trade.setTotalAmount("1.00");
        return trade;
    }

    @Test
    void configReadIsAllowedForAliPayList() {
        asUser("reader", "aliPay:list");
        when(alipayService.find()).thenReturn(storedConfig());
        ResponseEntity<AlipayConfigRedactedDto> response = controller.queryAliConfig();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("appid-2026083100012345", response.getBody().getAppId());
    }

    @Test
    void configReadIsForbiddenWithoutAliPayList() {
        asUser("lowpriv", "email:list");
        assertThrows(AccessDeniedException.class, () -> controller.queryAliConfig());
    }

    @Test
    void configReadNeverSerializesStoredPrivateKey() throws Exception {
        asUser("admin", "admin");
        when(alipayService.find()).thenReturn(storedConfig());
        AlipayConfigRedactedDto body = controller.queryAliConfig().getBody();
        assertEquals(SecretMask.SENTINEL, body.getPrivateKey());
        String json = objectMapper.writeValueAsString(body);
        assertFalse(json.contains(STORED_PRIVATE_KEY), "脱敏读视图不得回显已存私钥: " + json);
        assertTrue(json.contains(SecretMask.SENTINEL));
        assertTrue(json.contains("appid-2026083100012345"));
    }

    @Test
    void configReadWithNoStoredConfigLeavesSecretNull() {
        asUser("admin", "admin");
        when(alipayService.find()).thenReturn(new AlipayConfig());
        AlipayConfigRedactedDto body = controller.queryAliConfig().getBody();
        assertNull(body.getPrivateKey(), "无已存配置时不得伪造掩码，保持首次录入表单行为");
    }

    @Test
    void configUpdateIsForbiddenWithoutAliPayEdit() {
        asUser("reader", "aliPay:list");
        assertThrows(AccessDeniedException.class,
                () -> controller.updateAliPayConfig(updateDto("")));
        verify(alipayService, never()).config(any());
    }

    @Test
    void blankPrivateKeyOnUpdateRetainsStoredSecret() throws Exception {
        asUser("editor", "aliPay:edit");
        when(alipayService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK, controller.updateAliPayConfig(updateDto("")).getStatusCode());
        ArgumentCaptor<AlipayConfig> captor = ArgumentCaptor.forClass(AlipayConfig.class);
        verify(alipayService).config(captor.capture());
        assertEquals(STORED_PRIVATE_KEY, captor.getValue().getPrivateKey());
        assertEquals(1L, captor.getValue().getId());
    }

    @Test
    void nullPrivateKeyOnUpdateRetainsStoredSecret() throws Exception {
        asUser("editor", "aliPay:edit");
        when(alipayService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK, controller.updateAliPayConfig(updateDto(null)).getStatusCode());
        ArgumentCaptor<AlipayConfig> captor = ArgumentCaptor.forClass(AlipayConfig.class);
        verify(alipayService).config(captor.capture());
        assertEquals(STORED_PRIVATE_KEY, captor.getValue().getPrivateKey());
    }

    @Test
    void newPrivateKeyReplacesStoredSecret() throws Exception {
        asUser("editor", "aliPay:edit");
        when(alipayService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK,
                controller.updateAliPayConfig(updateDto("rotated-key-2026")).getStatusCode());
        ArgumentCaptor<AlipayConfig> captor = ArgumentCaptor.forClass(AlipayConfig.class);
        verify(alipayService).config(captor.capture());
        assertEquals("rotated-key-2026", captor.getValue().getPrivateKey());
    }

    @Test
    void sentinelPrivateKeyOnUpdateRetainsStoredSecret() throws Exception {
        asUser("editor", "aliPay:edit");
        when(alipayService.find()).thenReturn(storedConfig());
        assertEquals(HttpStatus.OK,
                controller.updateAliPayConfig(updateDto(SecretMask.SENTINEL)).getStatusCode());
        ArgumentCaptor<AlipayConfig> captor = ArgumentCaptor.forClass(AlipayConfig.class);
        verify(alipayService).config(captor.capture());
        assertEquals(STORED_PRIVATE_KEY, captor.getValue().getPrivateKey());
    }

    @Test
    void storedSecretNeverEqualsSentinelAfterAnyUpdatePath() throws Exception {
        asUser("editor", "aliPay:edit");
        for (String submitted : new String[] {null, "", "  ", SecretMask.SENTINEL, "rotated-key-2026"}) {
            Mockito.reset(alipayService);
            when(alipayService.find()).thenReturn(storedConfig());
            assertEquals(HttpStatus.OK,
                    controller.updateAliPayConfig(updateDto(submitted)).getStatusCode(), "path: " + submitted);
            ArgumentCaptor<AlipayConfig> captor = ArgumentCaptor.forClass(AlipayConfig.class);
            verify(alipayService).config(captor.capture());
            assertNotEquals(SecretMask.SENTINEL, captor.getValue().getPrivateKey(), "path: " + submitted);
            if (SecretMask.isRetained(submitted)) {
                assertEquals(STORED_PRIVATE_KEY, captor.getValue().getPrivateKey(), "path: " + submitted);
            } else {
                assertEquals(submitted, captor.getValue().getPrivateKey(), "path: " + submitted);
            }
        }
    }

    @Test
    void firstTimeConfigWithoutPrivateKeyIsRejected() throws Exception {
        asUser("editor", "aliPay:edit");
        when(alipayService.find()).thenReturn(new AlipayConfig());
        assertThrows(BadRequestException.class,
                () -> controller.updateAliPayConfig(updateDto("")));
        assertThrows(BadRequestException.class,
                () -> controller.updateAliPayConfig(updateDto(SecretMask.SENTINEL)));
        verify(alipayService, never()).config(any());
    }

    @Test
    void toPayAsPcIsAllowedForAliPayPay() throws Exception {
        asUser("payer", "aliPay:pay");
        when(alipayService.find()).thenReturn(storedConfig());
        when(alipayService.toPayAsPc(any(), any())).thenReturn("https://openapi.example.test/pay");
        ResponseEntity<String> response = controller.toPayAsPc(trade());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://openapi.example.test/pay", response.getBody());
    }

    @Test
    void toPayAsPcIsForbiddenWithoutAliPayPay() throws Exception {
        asUser("editor", "aliPay:edit");
        assertThrows(AccessDeniedException.class, () -> controller.toPayAsPc(trade()));
        verify(alipayService, never()).toPayAsPc(any(), any());
    }

    @Test
    void toPayAsWebIsAllowedForAliPayPay() throws Exception {
        asUser("payer", "aliPay:pay");
        when(alipayService.find()).thenReturn(storedConfig());
        when(alipayService.toPayAsWeb(any(), any())).thenReturn("https://openapi.example.test/wap");
        ResponseEntity<String> response = controller.toPayAsWeb(trade());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("https://openapi.example.test/wap", response.getBody());
    }

    @Test
    void toPayAsWebIsForbiddenWithoutAliPayPay() throws Exception {
        asUser("editor", "aliPay:edit");
        assertThrows(AccessDeniedException.class, () -> controller.toPayAsWeb(trade()));
        verify(alipayService, never()).toPayAsWeb(any(), any());
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
        AliPayService productionAliPayService() {
            return alipayService;
        }

        @Bean
        AlipayUtils productionAlipayUtils() {
            return mock(AlipayUtils.class);
        }

        @Bean
        AliPayController aliPayController() {
            return new AliPayController(productionAlipayUtils(), productionAliPayService());
        }
    }
}
