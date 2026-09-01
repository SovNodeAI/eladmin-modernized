/*
 *  Copyright 2026 SovNode (method security 403 response shape); see NOTICE.
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
package me.zhengjie.exception.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SEC-005 响应形状测试：方法安全拒绝（AccessDeniedException 及其在当前 Spring Security 7.1
 * 下抛出的 AuthorizationDeniedException 子类）必须映射为 HTTP 403，响应体保持
 * {@link ApiError} 的 status/timestamp/message 三字段契约，且被拒调用不产生任何变更；
 * 其余 Throwable 仍走默认 400 行为。
 *
 * @author SovNode
 */
@Tag("behavioral")
class GlobalExceptionHandlerAccessDeniedTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @RestController
    static class GuardedResource {
        final List<String> mutations = new ArrayList<>();

        @PostMapping("/sov-1321/mutate")
        public Map<String, Object> mutate(@RequestBody Map<String, Object> payload) {
            mutations.add("mutated:" + payload.get("name"));
            return Map.of("ok", true);
        }

        @PostMapping("/sov-1321/denied-before-mutate")
        public Map<String, Object> deniedBeforeMutate(@RequestBody Map<String, Object> payload) {
            throw new AccessDeniedException("只有管理员可以执行该操作");
        }

        @PostMapping("/sov-1321/boom")
        public Map<String, Object> boom(@RequestBody Map<String, Object> payload) {
            throw new IllegalStateException("未预期失败");
        }
    }

    private static GuardedResource methodSecured(GuardedResource target) {
        AuthorizationManager<MethodInvocation> denyAll =
                (authentication, object) -> new AuthorizationDecision(false);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new AuthorizationManagerBeforeMethodInterceptor(Pointcut.TRUE, denyAll));
        return (GuardedResource) proxyFactory.getProxy();
    }

    private static MockMvc mockMvcFor(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static void assertStandardErrorShape(String body, int expectedStatus, String expectedMessage)
            throws Exception {
        JsonNode node = OBJECT_MAPPER.readTree(body);
        Set<String> fields = new TreeSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        assertEquals(new TreeSet<>(Set.of("status", "timestamp", "message")), fields,
                "错误契约必须且只能包含三个字段: " + body);
        assertEquals(expectedStatus, node.get("status").asInt(), "status 字段: " + body);
        assertTrue(node.get("timestamp").isNumber(), "timestamp 必须为数值: " + body);
        assertEquals(expectedMessage, node.get("message").asText(), "message 字段: " + body);
    }

    /**
     * 真实方法安全拦截器（当前 head 上 @PreAuthorize 的拒绝路径）在方法体执行前抛出
     * AuthorizationDeniedException，且被拒调用未触达业务变更。
     */
    @Test
    void methodSecurityInterceptorDeniesBeforeBodyWithoutMutation() {
        GuardedResource target = new GuardedResource();
        GuardedResource proxied = methodSecured(target);

        assertThrows(AuthorizationDeniedException.class,
                () -> proxied.mutate(Map.of("name", "alice")));

        assertTrue(target.mutations.isEmpty(), "被拒调用不得产生任何变更");
    }

    /**
     * 方法安全拒绝经 DispatcherServlet 到达统一异常处理后的 HTTP 表现：403 + 标准错误契约 + 无变更。
     */
    @Test
    void methodSecurityDeniedReturns403WithStandardErrorShapeAndNoMutation() throws Exception {
        GuardedResource target = new GuardedResource();
        GuardedResource proxied = methodSecured(target);

        String body = mockMvcFor(proxied)
                .perform(post("/sov-1321/mutate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"alice\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = OBJECT_MAPPER.readTree(body);
        assertEquals(3, node.size(), "错误契约必须且只能包含三个字段: " + body);
        assertTrue(node.get("timestamp").isNumber(), "timestamp 必须为数值: " + body);
        assertTrue(target.mutations.isEmpty(), "被拒调用不得产生任何变更");
    }

    /**
     * 基类 AccessDeniedException（含透传的拒绝原因）同样映射为 403。
     */
    @Test
    void accessDeniedExceptionReturns403WithReasonMessage() throws Exception {
        GuardedResource controller = new GuardedResource();

        String body = mockMvcFor(controller)
                .perform(post("/sov-1321/denied-before-mutate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"alice\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andReturn().getResponse().getContentAsString();

        assertStandardErrorShape(body, 403, "只有管理员可以执行该操作");
        assertTrue(controller.mutations.isEmpty(), "被拒调用不得产生任何变更");
    }

    /**
     * 其余 Throwable 保持既有默认 400 行为，不受新增处理器影响。
     */
    @Test
    void otherThrowablesKeepDefault400Behavior() throws Exception {
        String body = mockMvcFor(new GuardedResource())
                .perform(post("/sov-1321/boom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"alice\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertStandardErrorShape(body, 400, "未预期失败");
    }

    /**
     * 直接单元断言：新处理器对 AccessDeniedException 与 AuthorizationDeniedException 均返回 403。
     */
    @Test
    void handlerMapsBothDenialTypesTo403() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiError> base = handler.accessDeniedException(
                new AccessDeniedException("denied-base"));
        assertEquals(403, base.getStatusCode().value());
        assertEquals(403, base.getBody().getStatus());
        assertEquals("denied-base", base.getBody().getMessage());

        ResponseEntity<ApiError> subclass = handler.accessDeniedException(
                new AuthorizationDeniedException("denied-subclass"));
        assertEquals(403, subclass.getStatusCode().value());
        assertEquals(403, subclass.getBody().getStatus());
        assertEquals("denied-subclass", subclass.getBody().getMessage());
    }
}
