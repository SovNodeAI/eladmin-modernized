package me.zhengjie.utils;

import me.zhengjie.annotation.rest.AnonymousAccess;
import me.zhengjie.annotation.rest.AnonymousDeleteMapping;
import me.zhengjie.annotation.rest.AnonymousGetMapping;
import me.zhengjie.annotation.rest.AnonymousPatchMapping;
import me.zhengjie.annotation.rest.AnonymousPostMapping;
import me.zhengjie.annotation.rest.AnonymousPutMapping;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@org.junit.jupiter.api.Tag("behavioral")
class AnonTagUtilsTest {

    @Test
    void findsAnonymousRoutesWithAntPathMatching() throws Exception {
        assertAnonymousRoutes(null);
    }

    @Test
    void findsAnonymousRoutesWithParsedPathMatching() throws Exception {
        RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
        options.setPatternParser(new PathPatternParser());

        assertAnonymousRoutes(options);
    }

    private void assertAnonymousRoutes(RequestMappingInfo.BuilderConfiguration options) throws Exception {
        AnonymousController controller = new AnonymousController();
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(mapping("/anonymous/get", RequestMethod.GET, options), handler(controller, "get"));
        handlers.put(mapping("/anonymous/post", RequestMethod.POST, options), handler(controller, "post"));
        handlers.put(mapping("/anonymous/put", RequestMethod.PUT, options), handler(controller, "put"));
        handlers.put(mapping("/anonymous/patch", RequestMethod.PATCH, options), handler(controller, "patch"));
        handlers.put(mapping("/anonymous/delete", RequestMethod.DELETE, options), handler(controller, "delete"));
        handlers.put(mapping("/anonymous/all", null, options), handler(controller, "all"));
        handlers.put(mapping("/private", RequestMethod.GET, options), handler(controller, "privateRoute"));

        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandlerMethods()).thenReturn(handlers);
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getBean("requestMappingHandlerMapping")).thenReturn(mapping);

        Map<String, Set<String>> routes = AnonTagUtils.getAnonymousUrl(context);

        assertEquals(Set.of("/anonymous/get"), routes.get("GET"));
        assertEquals(Set.of("/anonymous/post"), routes.get("POST"));
        assertEquals(Set.of("/anonymous/put"), routes.get("PUT"));
        assertEquals(Set.of("/anonymous/patch"), routes.get("PATCH"));
        assertEquals(Set.of("/anonymous/delete"), routes.get("DELETE"));
        assertEquals(Set.of("/anonymous/all"), routes.get("ALL"));
        assertEquals(Set.of(
                "/anonymous/get",
                "/anonymous/post",
                "/anonymous/put",
                "/anonymous/patch",
                "/anonymous/delete",
                "/anonymous/all"
        ), AnonTagUtils.getAllAnonymousUrl(context));
    }

    private RequestMappingInfo mapping(
            String path,
            RequestMethod method,
            RequestMappingInfo.BuilderConfiguration options
    ) {
        RequestMappingInfo.Builder builder = RequestMappingInfo.paths(path);
        if (method != null) {
            builder.methods(method);
        }
        if (options != null) {
            builder.options(options);
        }
        return builder.build();
    }

    private HandlerMethod handler(AnonymousController controller, String methodName) throws Exception {
        Method method = AnonymousController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(controller, method);
    }

    private static class AnonymousController {

        @AnonymousGetMapping
        void get() {
        }

        @AnonymousPostMapping
        void post() {
        }

        @AnonymousPutMapping
        void put() {
        }

        @AnonymousPatchMapping
        void patch() {
        }

        @AnonymousDeleteMapping
        void delete() {
        }

        @AnonymousAccess
        void all() {
        }

        void privateRoute() {
        }
    }
}
