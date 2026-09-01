/*
 *  Copyright 2026 SovNode (password administration audit closure); see NOTICE.
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
package me.zhengjie.modules.system.rest;

import cn.hutool.jwt.JWTUtil;
import me.zhengjie.annotation.Log;
import me.zhengjie.aspect.LogAspect;
import me.zhengjie.config.AuthorityConfig;
import me.zhengjie.modules.system.service.DataService;
import me.zhengjie.modules.system.service.DeptService;
import me.zhengjie.modules.system.service.RoleService;
import me.zhengjie.modules.system.service.UserService;
import me.zhengjie.modules.system.service.VerifyService;
import me.zhengjie.service.SysLogService;
import me.zhengjie.utils.SecurityUtils;
import me.zhengjie.utils.SpringBeanHolder;
import org.aspectj.lang.ProceedingJoinPoint;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 重置密码端点的授权与审计测试：走真实的 @EnableMethodSecurity 代理和
 * 真实的 LogAspect 审计切面。user:edit 授权门（安全切片 A）不得回退；
 * 管理员重置必须留下审计记录；审计参数与响应都不携带生成的明文口令，
 * 两次重置必须产生不同口令（无可预测口令路径）。
 */
@Tag("behavioral")
class UserControllerResetPasswordAuditTest {

    private static AnnotationConfigApplicationContext context;
    private static UserDetailsService userDetailsService;
    private static SysLogService sysLogService;
    private static UserService userService;
    private static UserController controller;

    @BeforeAll
    static void buildAuditedSecuredController() {
        userDetailsService = mock(UserDetailsService.class);
        sysLogService = mock(SysLogService.class);
        userService = mock(UserService.class);
        context = new AnnotationConfigApplicationContext(ResetPwdConfig.class);
        controller = context.getBean(UserController.class);
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
        Mockito.reset(userDetailsService, sysLogService, userService);
    }

    private static void asUser(String username, String... permissions) {
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        String token = JWTUtil.createToken(Map.of("sub", username),
                "sov-1038-test-key".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/users/resetPwd");
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
    void resetPwdIsAllowedForUserEdit() {
        asUser("editor", "user:edit");
        ResponseEntity<Object> response = controller.resetPwd(Set.of(2L));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String password = (String) ((Map<?, ?>) response.getBody()).get("password");
        assertTrue(password != null && password.length() == 12,
                "reset must return a server generated one time password");
    }

    @Test
    void resetPwdIsForbiddenWithoutUserEdit() {
        asUser("viewer", "user:list");
        assertThrows(AccessDeniedException.class, () -> controller.resetPwd(Set.of(2L)));
        verify(userService, times(0)).resetPwd(anySet(), anyString());
    }

    @Test
    void resetPwdIsAuditLoggedWithoutTheGeneratedPassword() {
        asUser("editor", "user:edit");
        ResponseEntity<Object> response = controller.resetPwd(Set.of(2L));
        String returnedPassword = (String) ((Map<?, ?>) response.getBody()).get("password");

        ArgumentCaptor<ProceedingJoinPoint> joinPointCaptor =
                ArgumentCaptor.forClass(ProceedingJoinPoint.class);
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        verify(sysLogService, times(1)).save(usernameCaptor.capture(), anyString(), anyString(),
                joinPointCaptor.capture(), Mockito.any());
        assertEquals("editor", usernameCaptor.getValue());

        ProceedingJoinPoint joinPoint = joinPointCaptor.getValue();
        java.lang.reflect.Method auditedMethod = ((org.aspectj.lang.reflect.MethodSignature)
                joinPoint.getSignature()).getMethod();
        Log audit = auditedMethod.getAnnotation(Log.class);
        assertNotNull(audit, "resetPwd must carry the @Log audit annotation");
        assertEquals("重置密码", audit.value());
        assertEquals("resetPwd", joinPoint.getSignature().getName());

        for (Object argument : joinPoint.getArgs()) {
            assertNotEquals(returnedPassword, argument,
                    "audit parameters must never carry the generated password");
        }
        assertTrue(joinPoint.getArgs()[0] instanceof Set,
                "resetPwd audit parameters are the target user ids only");
    }

    @Test
    void resetPwdGeneratesANonPredictablePasswordEachTime() {
        asUser("editor", "user:edit");
        String first = (String) ((Map<?, ?>) controller.resetPwd(Set.of(2L)).getBody()).get("password");
        String second = (String) ((Map<?, ?>) controller.resetPwd(Set.of(3L)).getBody()).get("password");
        assertNotEquals(first, second, "two resets must not produce the same password");
    }

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
    @org.springframework.context.annotation.EnableAspectJAutoProxy
    static class ResetPwdConfig {

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
        SysLogService productionSysLogService() {
            return sysLogService;
        }

        @Bean
        LogAspect logAspect() {
            return new LogAspect(productionSysLogService());
        }

        @Bean
        UserService productionUserService() {
            return userService;
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        UserController userController(DataService dataService, DeptService deptService,
                RoleService roleService, VerifyService verifyService) {
            return new UserController(passwordEncoder(), productionUserService(),
                    dataService, deptService, roleService, verifyService);
        }

        @Bean
        DataService dataService() {
            return mock(DataService.class);
        }

        @Bean
        DeptService deptService() {
            return mock(DeptService.class);
        }

        @Bean
        RoleService roleService() {
            return mock(RoleService.class);
        }

        @Bean
        VerifyService verifyService() {
            return mock(VerifyService.class);
        }
    }
}
