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
import me.zhengjie.domain.AlipayConfig;
import me.zhengjie.exception.handler.GlobalExceptionHandler;
import me.zhengjie.service.AliPayService;
import me.zhengjie.utils.AlipayUtils;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 板块闭合表 alipay-config-read / alipay-config-update / alipay-pay-pc / alipay-pay-web
 * 的低权限负向对照。主体是真实 DispatcherServlet + 真实 @EnableMethodSecurity 代理 +
 * 真实 AuthorityConfig("el") 权限判定 + 真实 JWT 请求头解析链路，服务层全部为 mock。
 * 请求体均为合法业务报文（占位密钥串，非真实凭据）。低权限主体持有闭包表族
 * （LocalStorage/AliPay/Email 三个控制器）内的全部权限，仅缺少被测路由声明的权限；
 * 权限串在运行时从 @PreAuthorize 注解解析。修复前（52eb188）四条路由无 @PreAuthorize，
 * 用例以授权原因失败；集成头必须返回 HTTP 403、标准 ApiError 错误体，且 AliPayService 零调用。
 * 匿名回调路由（/return、/notify）不属于本对照，保持原状。
 */
@Tag("behavioral")
class AliPayLowPrivilegeNegativeControlTest {

    private static final String AUTHORIZATION = "Authorization";
    private static final List<Class<?>> GUARDED_FAMILY = List.of(
            LocalStorageController.class, AliPayController.class, EmailController.class);

    private static AnnotationConfigApplicationContext context;
    private static AliPayService aliPayService;
    private static AlipayUtils alipayUtils;
    private static UserDetailsService userDetailsService;
    private static MockMvc mockMvc;

    @BeforeAll
    static void bootSecuredRoutes() {
        aliPayService = mock(AliPayService.class);
        alipayUtils = mock(AlipayUtils.class);
        userDetailsService = mock(UserDetailsService.class);
        when(alipayUtils.getOrderCode()).thenReturn("20260831000000000000");
        context = new AnnotationConfigApplicationContext(SecuredRoutesConfig.class);
        mockMvc = MockMvcBuilders.standaloneSetup(context.getBean(AliPayController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(productionJsonConverter())
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
        Mockito.reset(userDetailsService, aliPayService);
    }

    @Test
    void configReadIsForbiddenForPrincipalLackingOnlyTheNewPermission() throws Exception {
        String token = lowPrivilegePrincipalLacking("queryAliConfig");
        mockMvc.perform(get("/api/aliPay").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(aliPayService);
    }

    @Test
    void configUpdateIsForbiddenForPrincipalLackingOnlyTheNewPermission() throws Exception {
        String token = lowPrivilegePrincipalLacking("updateAliPayConfig");
        mockMvc.perform(put("/api/aliPay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAlipayConfigBody())
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(aliPayService);
    }

    @Test
    void payAsPcIsForbiddenForPrincipalLackingOnlyTheNewPermission() throws Exception {
        String token = lowPrivilegePrincipalLacking("toPayAsPc");
        mockMvc.perform(post("/api/aliPay/toPayAsPC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTradeBody())
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(aliPayService);
    }

    @Test
    void payAsWebIsForbiddenForPrincipalLackingOnlyTheNewPermission() throws Exception {
        String token = lowPrivilegePrincipalLacking("toPayAsWeb");
        mockMvc.perform(post("/api/aliPay/toPayAsWeb")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validTradeBody())
                        .header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.message").isNotEmpty());
        verifyNoInteractions(aliPayService);
    }

    @Test
    void harnessStillAllowsRoutesThePrincipalIsEntitledTo() throws Exception {
        String token = asAuthenticated("lowpriv-alipay-positive-control", familyPermissions());
        when(aliPayService.find()).thenReturn(seededConfig());
        mockMvc.perform(get("/api/aliPay").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** 合法的配置报文：字段全部为占位测试值，不含真实凭据。 */
    private static String validAlipayConfigBody() {
        return "{\"id\":1,\"appId\":\"2021000000000000\","
                + "\"privateKey\":\"TEST-ONLY-merchant-private-key-placeholder\","
                + "\"publicKey\":\"TEST-ONLY-alipay-public-key-placeholder\","
                + "\"signType\":\"RSA2\",\"gatewayUrl\":\"https://openapi.alipaydev.com/gateway.do\","
                + "\"charset\":\"utf-8\",\"notifyUrl\":\"https://ops.example.test/api/aliPay/notify\","
                + "\"returnUrl\":\"https://ops.example.test/api/aliPay/return\",\"format\":\"JSON\","
                + "\"sysServiceProviderId\":\"2088000000000000\"}";
    }

    /** 合法的交易报文：满足 TradeVo 的 @NotBlank 约束。 */
    private static String validTradeBody() {
        return "{\"body\":\"测试商品描述\",\"subject\":\"测试商品\",\"totalAmount\":\"0.01\"}";
    }

    private static AlipayConfig seededConfig() {
        AlipayConfig config = new AlipayConfig();
        config.setId(1L);
        config.setAppId("2021000000000000");
        config.setPrivateKey("TEST-ONLY-merchant-private-key-placeholder");
        config.setPublicKey("TEST-ONLY-alipay-public-key-placeholder");
        return config;
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
        for (Method method : AliPayController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && !method.isSynthetic()) {
                return method;
            }
        }
        throw new IllegalStateException("AliPayController 未找到处理器方法 " + methodName);
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
        converter.setFastJsonConfig(new FastJsonConfig());
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
        AliPayService productionAliPayService() {
            return aliPayService;
        }

        @Bean
        AlipayUtils productionAlipayUtils() {
            return alipayUtils;
        }

        @Bean
        AliPayController aliPayController() {
            return new AliPayController(productionAlipayUtils(), productionAliPayService());
        }
    }
}
