/*
 *  Copyright 2026 SovNode (eladmin modernization line)
 *
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
package me.zhengjie;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 生产 fail closed 启动契约的负向测试（SOV-1037）：
 *
 * 1. 打包产物未显式选择 profile 时，不得静默回退 dev，启动必须失败；
 * 2. prod 缺失数据源/Redis 认证输入时，启动必须失败；
 * 3. 输入被显式置空时同样失败（占位符默认值救不了）；
 * 4. 已知公开演示口令（上游 123456）被拒绝；
 * 5. 所有必要输入齐全时守卫放行（随后才轮到真实基础设施错误）。
 *
 * 全部断言都要求失败发生在 ApplicationStartedEvent 之前，即早于
 * "Started AppRun in" 启动完成标记。守卫在 BeanFactory 后处理阶段抛出，
 * 早于数据源与 Web 服务器创建，因此这些测试无需 MySQL/Redis/Docker。
 *
 * @author SovNode
 */
@Tag("behavioral")
class BootstrapFailClosedTest {

    private static final String GUARD_PREFIX = "BootstrapFailClosedGuard: ";

    private final List<ApplicationStartedEvent> startedEvents = new CopyOnWriteArrayList<>();

    private SpringApplicationBuilder guardBuilder() {
        return new SpringApplicationBuilder(AppRun.class)
                .web(WebApplicationType.NONE)
                .listeners((ApplicationListener<ApplicationStartedEvent>) startedEvents::add);
    }

    private static Throwable requireStartupFailure(SpringApplicationBuilder builder, String... args) {
        ConfigurableApplicationContext context = null;
        try {
            context = builder.run(args);
        } catch (RuntimeException expected) {
            // Spring Boot wraps every aborted refresh as IllegalStateException("Application run failed"),
            // but downstream infrastructure failures can surface as BeansException subtypes; both prove
            // the boot never reached the started marker.
            Throwable root = expected;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            return root;
        }
        if (context != null) {
            context.close();
        }
        fail("startup must fail closed");
        return null;
    }

    private static String fullChain(Throwable head) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = head; t != null; t = t.getCause()) {
            chain.append(String.valueOf(t.getMessage())).append(" | ");
        }
        return chain.toString();
    }

    @Test
    @Timeout(120)
    void packagedLaunchWithoutExplicitProfileFailsClosedBeforeTheStartedMarker() {
        Throwable root = requireStartupFailure(guardBuilder());
        assertNotNull(root.getMessage());
        String chain = fullChain(root);
        assertTrue(chain.contains(GUARD_PREFIX),
                "the fail closed guard must own the failure, got: " + chain);
        assertTrue(chain.contains("no Spring profile is active"),
                "the contract message must name the missing explicit profile, got: " + chain);
        assertTrue(chain.contains("SPRING_PROFILES_ACTIVE=dev"),
                "the contract message must point at the local onboarding contract, got: " + chain);
        assertTrue(startedEvents.isEmpty(),
                "startup must fail before the ApplicationStartedEvent marker");
    }

    @Test
    @Timeout(120)
    void prodProfileWithoutAuthenticationInputsFailsClosedBeforeTheStartedMarker() {
        Throwable root = requireStartupFailure(guardBuilder(), "--spring.profiles.active=prod");
        String chain = fullChain(root);
        assertTrue(chain.contains(GUARD_PREFIX),
                "the fail closed guard must own the failure, got: " + chain);
        for (String envName : new String[]{"DB_USER", "DB_PWD", "REDIS_PWD", "JWT_SECRET", "RSA_PRIVATE_KEY"}) {
            assertTrue(chain.contains(envName),
                    "the failure must enumerate the missing input " + envName + ", got: " + chain);
        }
        assertTrue(startedEvents.isEmpty(),
                "startup must fail before the ApplicationStartedEvent marker");
    }

    @Test
    @Timeout(120)
    void prodProfileRejectsExplicitlyBlankAuthenticationInputs() {
        Throwable root = requireStartupFailure(guardBuilder(),
                "--spring.profiles.active=prod",
                "--spring.datasource.druid.username=",
                "--spring.datasource.druid.password=",
                "--spring.data.redis.password=",
                "--jwt.base64-secret=",
                "--rsa.private_key=");
        String chain = fullChain(root);
        assertTrue(chain.contains(GUARD_PREFIX), "guard must own the failure, got: " + chain);
        assertTrue(chain.contains("spring.datasource.druid.username (DB_USER) is blank"),
                "blank DB user must be rejected by name, got: " + chain);
        assertTrue(chain.contains("spring.datasource.druid.password (DB_PWD) is blank"),
                "blank DB password must be rejected by name, got: " + chain);
        assertTrue(chain.contains("spring.data.redis.password (REDIS_PWD) is blank"),
                "blank Redis password must be rejected by name, got: " + chain);
        assertTrue(chain.contains("jwt.base64-secret (JWT_SECRET) is blank"),
                "blank JWT secret must be rejected by name, got: " + chain);
        assertTrue(chain.contains("rsa.private_key (RSA_PRIVATE_KEY) is blank"),
                "blank RSA key must be rejected by name, got: " + chain);
        assertTrue(startedEvents.isEmpty(),
                "startup must fail before the ApplicationStartedEvent marker");
    }

    @Test
    @Timeout(120)
    void prodProfileRejectsTheKnownPublicDemoPassword() {
        Throwable root = requireStartupFailure(guardBuilder(),
                "--spring.profiles.active=prod",
                "--spring.datasource.druid.username=eladmin_operator",
                "--spring.datasource.druid.password=123456",
                "--spring.data.redis.password=123456",
                "--jwt.base64-secret=" + "A".repeat(64),
                "--rsa.private_key=unused-in-this-assertion");
        String chain = fullChain(root);
        assertTrue(chain.contains(GUARD_PREFIX), "guard must own the failure, got: " + chain);
        assertTrue(chain.contains("spring.datasource.druid.password (DB_PWD) carries a known public demo value"),
                "the shipped demo DB password must be refused, got: " + chain);
        assertTrue(chain.contains("spring.data.redis.password (REDIS_PWD) carries a known public demo value"),
                "the shipped demo Redis password must be refused, got: " + chain);
        assertTrue(startedEvents.isEmpty(),
                "startup must fail before the ApplicationStartedEvent marker");
    }

    @Test
    @Timeout(180)
    void prodProfileWithAllRequiredInputsPassesTheGuardAndFailsOnlyAtRealInfrastructure() {
        // 守卫放行后，启动才可能到达真实基础设施；这里用一个必然连接失败的
        // 本地地址证明失败点已经不在守卫（无 BootstrapFailClosedGuard 消息）。
        Throwable root = requireStartupFailure(guardBuilder(),
                "--spring.profiles.active=prod",
                "--spring.datasource.druid.url=jdbc:mysql://127.0.0.1:1/eladmin",
                "--spring.datasource.druid.username=eladmin_operator",
                "--spring.datasource.druid.password=a-real-production-style-password",
                "--spring.data.redis.password=a-real-production-style-password",
                "--jwt.base64-secret=" + "A".repeat(64),
                "--rsa.private_key=unused-in-this-assertion");
        String chain = fullChain(root);
        assertFalse(chain.contains(GUARD_PREFIX),
                "with every required input supplied the guard must pass the boot onward, got: " + chain);
        assertTrue(startedEvents.isEmpty(),
                "without real MySQL/Redis the boot still cannot reach the started marker");
    }
}
