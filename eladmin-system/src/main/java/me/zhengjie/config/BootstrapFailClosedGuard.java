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
package me.zhengjie.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 启动 fail closed 守卫（SOV-1037）。
 *
 * 在 BeanFactory 后处理阶段（早于数据源、Redis、Web 服务器等任何 bean 创建，
 * 因此必然早于 "Started AppRun in" 启动完成标记）检查生效的 profile 契约：
 *
 * 1. 未显式选择 profile（active 集合为空，或只含 Boot 隐式 "default"）：
 *    打包产物不再静默回退到 dev，直接启动失败；
 * 2. 显式选择 dev（含 dev,quartz）：本地显式契约，允许仓库内便捷默认值，
 *    守卫放行（JWT/RSA 等无默认占位符仍由配置解析层 fail closed）；
 * 3. 显式选择 prod（或任何非 dev profile）：数据源与 Redis 认证输入、
 *    JWT 密钥、RSA 私钥必须为非空有效值，拒绝空串与已知演示口令
 *    （上游历史口令 123456）；Druid 控制台启用时登录凭据同样必须非空。
 *
 * 读取的是最终生效属性（环境变量、系统属性、命令行、测试动态属性合并后
 * 的结果），因此既覆盖占位符缺失（解析失败被转换为明确的契约错误），
 * 也覆盖占位符被显式置空的场景。
 *
 * @author SovNode
 */
@Component
public class BootstrapFailClosedGuard implements BeanFactoryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(BootstrapFailClosedGuard.class);

    /**
     * 上游历史数据源/控制台口令字面量，SOV-770 普查确认的公开演示值；
     * 生产显式输入命中即拒绝。
     */
    private static final Set<String> KNOWN_DEMO_VALUES = Set.of("123456");

    /** HS256 安全下限：Base64 编码后的 32 字节密钥至少 44 个字符。 */
    private static final int JWT_SECRET_MIN_BASE64_LENGTH = 43;

    private static final String GUARD_PREFIX = "BootstrapFailClosedGuard: ";

    static final String NO_PROFILE_MESSAGE = GUARD_PREFIX
            + "no Spring profile is active; a packaged launch must not fall back to dev implicitly. "
            + "Set SPRING_PROFILES_ACTIVE=dev for local onboarding (tools/onboard-local.sh emits local.env) "
            + "or SPRING_PROFILES_ACTIVE=prod with the required production inputs.";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        Environment environment = beanFactory.getBean(Environment.class);
        Set<String> active = new LinkedHashSet<>(Arrays.asList(environment.getActiveProfiles()));

        if (active.isEmpty() || (active.size() == 1 && active.contains("default"))) {
            throw new IllegalStateException(NO_PROFILE_MESSAGE);
        }
        if (active.contains("dev")) {
            log.info("{}explicit dev profile selected; local convenience defaults are allowed", GUARD_PREFIX);
            return;
        }

        FailClosedErrors errors = new FailClosedErrors();
        requireNonBlank(environment, errors, "spring.datasource.druid.username", "DB_USER");
        requirePassword(environment, errors, "spring.datasource.druid.password", "DB_PWD");
        requirePassword(environment, errors, "spring.data.redis.password", "REDIS_PWD");

        String jwtSecret = resolveOrNull(environment, errors, "jwt.base64-secret", "JWT_SECRET");
        if (jwtSecret != null) {
            if (jwtSecret.isBlank()) {
                errors.add("jwt.base64-secret (JWT_SECRET) is blank");
            } else if (jwtSecret.length() < JWT_SECRET_MIN_BASE64_LENGTH) {
                errors.add("jwt.base64-secret (JWT_SECRET) is shorter than "
                        + JWT_SECRET_MIN_BASE64_LENGTH + " Base64 characters");
            }
        }
        requireNonBlank(environment, errors, "rsa.private_key", "RSA_PRIVATE_KEY");

        if (environment.getProperty("spring.datasource.druid.stat-view-servlet.enabled",
                Boolean.class, Boolean.FALSE)) {
            requireNonBlank(environment, errors,
                    "spring.datasource.druid.stat-view-servlet.login-username", "DRUID_CONSOLE_USER");
            requirePassword(environment, errors,
                    "spring.datasource.druid.stat-view-servlet.login-password", "DRUID_CONSOLE_PWD");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(GUARD_PREFIX
                    + "the active profile set " + active + " requires explicit authentication inputs and "
                    + "the following are missing, empty, or known demo values:\n  - "
                    + String.join("\n  - ", errors.lines())
                    + "\n  Supply them via environment variables (see tools/onboard-local.sh for the "
                    + "local contract) and restart. Startup refuses to continue.");
        }
        log.info("{}active profiles {} carry the required authentication inputs", GUARD_PREFIX, active);
    }

    private void requireNonBlank(Environment environment, FailClosedErrors errors,
                                 String property, String envName) {
        String value = resolveOrNull(environment, errors, property, envName);
        if (value != null && value.isBlank()) {
            errors.add(property + " (" + envName + ") is blank");
        }
    }

    private void requirePassword(Environment environment, FailClosedErrors errors,
                                 String property, String envName) {
        String value = resolveOrNull(environment, errors, property, envName);
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            errors.add(property + " (" + envName + ") is blank");
        } else if (KNOWN_DEMO_VALUES.contains(value)) {
            errors.add(property + " (" + envName + ") carries a known public demo value");
        }
    }

    /**
     * 读取生效属性。占位符缺失（例如未设置 DB_PWD 导致 ${DB_PWD} 无法解析）时
     * 把底层解析异常转换为带契约名字的明确错误，而不是任由
     * "Could not resolve placeholder" 在别处随机爆出。
     */
    private String resolveOrNull(Environment environment, FailClosedErrors errors,
                                 String property, String envName) {
        try {
            return environment.getProperty(property);
        } catch (RuntimeException ex) {
            errors.add(property + " (" + envName + ") cannot be resolved: " + ex.getMessage());
            return null;
        }
    }

    private static final class FailClosedErrors {
        private final java.util.List<String> lines = new java.util.ArrayList<>();

        void add(String line) {
            lines.add(line);
        }

        boolean isEmpty() {
            return lines.isEmpty();
        }

        java.util.List<String> lines() {
            return lines;
        }
    }
}
