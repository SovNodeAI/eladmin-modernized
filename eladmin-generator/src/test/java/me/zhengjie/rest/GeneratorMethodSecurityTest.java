/*
 *  Copyright 2026 SovNode (generator authorization gap closure); see NOTICE.
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
import me.zhengjie.domain.ColumnInfo;
import me.zhengjie.domain.GenConfig;
import me.zhengjie.exception.BadRequestException;
import me.zhengjie.service.GenConfigService;
import me.zhengjie.service.GeneratorService;
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
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 代码生成与生成配置端点的方法级授权测试：走真实的 @EnableMethodSecurity 代理、
 * 真实的 AuthorityConfig("el") 权限判定与真实的 JWT 请求头解析路径，
 * 断言表/字段发现读需要 generator:list、保存与同步需要 generator:edit、
 * 预览/下载/落盘生成需要 generator:code、生成配置读写需要 genConfig:list/genConfig:edit，
 * 并守住 admin 直通与 generator.enabled 开关行为不被削弱。
 * 仅扫描源码文本无法发现缺失的 @PreAuthorize，这里必须真正发起调用。
 */
@Tag("behavioral")
class GeneratorMethodSecurityTest {

    private static AnnotationConfigApplicationContext context;
    private static GeneratorService generatorService;
    private static GenConfigService genConfigService;
    private static UserDetailsService userDetailsService;
    private static GeneratorController generatorController;
    private static GenConfigController genConfigController;

    @BeforeAll
    static void buildSecuredControllers() {
        generatorService = mock(GeneratorService.class);
        genConfigService = mock(GenConfigService.class);
        userDetailsService = mock(UserDetailsService.class);
        context = new AnnotationConfigApplicationContext();
        // 走真实 @Value 注入路径：仅补齐 generator.enabled 属性源。
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("sov-1320-test", Map.of("generator.enabled", "true")));
        context.register(MethodSecurityConfig.class);
        context.refresh();
        generatorController = context.getBean(GeneratorController.class);
        genConfigController = context.getBean(GenConfigController.class);
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
        Mockito.reset(userDetailsService, generatorService, genConfigService);
    }

    /**
     * 按生产链路构造当前用户：Authorization: Bearer <JWT(sub)> 请求头，
     * SecurityUtils 解析 sub，UserDetailsService 按用户名返回权限集合。
     */
    private static void asUser(String username, String... permissions) {
        SecurityUtils.header = "Authorization";
        SecurityUtils.tokenStartWith = "Bearer ";
        String token = JWTUtil.createToken(Map.of("sub", username),
                "sov-1320-test-key".getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/generator");
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

    private static ColumnInfo columnInfo() {
        ColumnInfo columnInfo = new ColumnInfo();
        columnInfo.setTableName("sys_user");
        columnInfo.setColumnName("username");
        columnInfo.setColumnType("varchar");
        return columnInfo;
    }

    private static GenConfig genConfig() {
        GenConfig genConfig = new GenConfig();
        genConfig.setId(1L);
        genConfig.setTableName("sys_user");
        genConfig.setAuthor("sov-qa");
        genConfig.setModuleName("eladmin-system");
        genConfig.setPath("src/main/java");
        return genConfig;
    }

    @Test
    void tableAndColumnDiscoveryReadsRequireGeneratorList() {
        asUser("cfgReader", "genConfig:list");
        assertThrows(AccessDeniedException.class, () -> generatorController.queryAllTables());
        assertThrows(AccessDeniedException.class, () -> generatorController.queryTables("user", 1, 10));
        assertThrows(AccessDeniedException.class, () -> generatorController.queryColumns("sys_user"));

        asUser("genReader", "generator:list");
        when(generatorService.getTables()).thenReturn(List.of());
        when(generatorService.getTables(eq("user"), any(int[].class))).thenReturn(new PageResult<>(List.of(), 0));
        when(generatorService.getColumns("sys_user")).thenReturn(List.of(columnInfo()));
        assertEquals(HttpStatus.OK, generatorController.queryAllTables().getStatusCode());
        assertEquals(HttpStatus.OK, generatorController.queryTables("user", 1, 10).getStatusCode());
        assertEquals(HttpStatus.OK, generatorController.queryColumns("sys_user").getStatusCode());
    }

    @Test
    void saveColumnRequiresGeneratorEditWithValidBody() {
        List<ColumnInfo> validBody = List.of(columnInfo());
        asUser("genReader", "generator:list");
        assertThrows(AccessDeniedException.class, () -> generatorController.saveColumn(validBody));
        verify(generatorService, never()).save(any());

        asUser("genEditor", "generator:edit");
        assertEquals(HttpStatus.OK, generatorController.saveColumn(validBody).getStatusCode());
        verify(generatorService).save(validBody);
    }

    @Test
    void syncColumnRequiresGeneratorEditWithValidBody() {
        List<String> validBody = List.of("sys_user");
        asUser("genReader", "generator:list");
        assertThrows(AccessDeniedException.class, () -> generatorController.syncColumn(validBody));
        verify(generatorService, never()).sync(any(), any());

        asUser("genEditor", "generator:edit");
        when(generatorService.getColumns("sys_user")).thenReturn(List.of(columnInfo()));
        when(generatorService.query("sys_user")).thenReturn(List.of(columnInfo()));
        assertEquals(HttpStatus.OK, generatorController.syncColumn(validBody).getStatusCode());
        verify(generatorService).sync(any(), any());
    }

    @Test
    void previewRequiresGeneratorCode() {
        ReflectionTestUtils.setField(generatorController, "generatorEnabled", false);
        asUser("genEditor", "generator:edit");
        assertThrows(AccessDeniedException.class, () -> generatorController.generatorCode(
                "sys_user", 1, new MockHttpServletRequest(), new MockHttpServletResponse()));

        asUser("coder", "generator:code");
        when(genConfigService.find("sys_user")).thenReturn(genConfig());
        when(generatorService.getColumns("sys_user")).thenReturn(List.of(columnInfo()));
        ResponseEntity<Object> preview = ResponseEntity.ok(Map.of("Entity.java", "// preview"));
        when(generatorService.preview(any(GenConfig.class), any())).thenReturn(preview);
        assertEquals(preview, generatorController.generatorCode(
                "sys_user", 1, new MockHttpServletRequest(), new MockHttpServletResponse()));
    }

    @Test
    void downloadRequiresGeneratorCode() {
        ReflectionTestUtils.setField(generatorController, "generatorEnabled", false);
        asUser("genEditor", "generator:edit");
        assertThrows(AccessDeniedException.class, () -> generatorController.generatorCode(
                "sys_user", 2, new MockHttpServletRequest(), new MockHttpServletResponse()));
        verify(generatorService, never()).download(any(), any(), any(), any());

        asUser("coder", "generator:code");
        when(genConfigService.find("sys_user")).thenReturn(genConfig());
        when(generatorService.getColumns("sys_user")).thenReturn(List.of(columnInfo()));
        assertEquals(HttpStatus.OK, generatorController.generatorCode(
                "sys_user", 2, new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode());
        verify(generatorService).download(any(GenConfig.class), any(), any(), any());
    }

    @Test
    void filesystemGenerationSinkRequiresGeneratorCode() {
        ReflectionTestUtils.setField(generatorController, "generatorEnabled", true);
        asUser("genEditor", "generator:edit");
        assertThrows(AccessDeniedException.class, () -> generatorController.generatorCode(
                "sys_user", 0, new MockHttpServletRequest(), new MockHttpServletResponse()));
        verify(generatorService, never()).generator(any(), any());

        asUser("coder", "generator:code");
        when(genConfigService.find("sys_user")).thenReturn(genConfig());
        when(generatorService.getColumns("sys_user")).thenReturn(List.of(columnInfo()));
        assertEquals(HttpStatus.OK, generatorController.generatorCode(
                "sys_user", 0, new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode());
        verify(generatorService).generator(any(GenConfig.class), any());

        // generator.enabled=false 的环境里，已授权用户仍被既有开关拦截：开关行为不被授权改动削弱。
        ReflectionTestUtils.setField(generatorController, "generatorEnabled", false);
        assertThrows(BadRequestException.class, () -> generatorController.generatorCode(
                "sys_user", 0, new MockHttpServletRequest(), new MockHttpServletResponse()));
    }

    @Test
    void adminAuthorityKeepsFullGeneratorFunction() {
        ReflectionTestUtils.setField(generatorController, "generatorEnabled", true);
        asUser("root", "admin");
        when(generatorService.getTables(anyString(), any(int[].class)))
                .thenReturn(new PageResult<>(List.of(), 0));
        when(generatorService.getColumns("sys_user")).thenReturn(List.of(columnInfo()));
        when(genConfigService.find("sys_user")).thenReturn(genConfig());
        assertEquals(HttpStatus.OK, generatorController.queryTables("user", 1, 10).getStatusCode());
        assertEquals(HttpStatus.OK, generatorController.saveColumn(List.of(columnInfo())).getStatusCode());
        assertEquals(HttpStatus.OK, generatorController.syncColumn(List.of("sys_user")).getStatusCode());
        assertEquals(HttpStatus.OK, generatorController.generatorCode(
                "sys_user", 0, new MockHttpServletRequest(), new MockHttpServletResponse()).getStatusCode());
        assertEquals(HttpStatus.OK, genConfigController.queryGenConfig("sys_user").getStatusCode());
        assertEquals(HttpStatus.OK, genConfigController.updateGenConfig(genConfig()).getStatusCode());
    }

    @Test
    void genConfigReadRequiresGenConfigList() {
        asUser("genReader", "generator:list");
        assertThrows(AccessDeniedException.class, () -> genConfigController.queryGenConfig("sys_user"));

        asUser("cfgReader", "genConfig:list");
        when(genConfigService.find("sys_user")).thenReturn(genConfig());
        assertEquals("sys_user", genConfigController.queryGenConfig("sys_user").getBody().getTableName());
    }

    @Test
    void genConfigUpdateRequiresGenConfigEditWithValidBody() {
        GenConfig validBody = genConfig();
        asUser("cfgReader", "genConfig:list");
        assertThrows(AccessDeniedException.class, () -> genConfigController.updateGenConfig(validBody));
        verify(genConfigService, never()).update(anyString(), any());

        asUser("cfgEditor", "genConfig:edit");
        when(genConfigService.update("sys_user", validBody)).thenReturn(validBody);
        assertEquals(HttpStatus.OK, genConfigController.updateGenConfig(validBody).getStatusCode());
        verify(genConfigService).update("sys_user", validBody);
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
        GeneratorService productionGeneratorService() {
            return generatorService;
        }

        @Bean
        GenConfigService productionGenConfigService() {
            return genConfigService;
        }

        @Bean
        GeneratorController generatorController() {
            return new GeneratorController(productionGeneratorService(), productionGenConfigService());
        }

        @Bean
        GenConfigController genConfigController() {
            return new GenConfigController(productionGenConfigService());
        }
    }
}
