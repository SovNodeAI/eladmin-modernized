/*
 *  Copyright 2026 SovNode
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 运行时契约测试：Spring Boot 3/4 只识别 spring.data.redis.* 命名空间，
 * 旧 spring.redis.* 块会静默失效导致 Lettuce 回退默认连接；Redisson 入门件
 * 属 Boot 2 时代且仓库内零消费者，必须整体移除。本测试静态校验配置与依赖，
 * 不启动 Spring 上下文，不依赖 MySQL 或 Redis；属迁移脚手架，计划事后清理。
 *
 * @author SovNode
 */
@org.junit.jupiter.api.Tag("static-contract")
class RedisRuntimeContractTest {

    private static Path repoRoot;
    private static Map<String, Object> application;

    @BeforeAll
    static void loadConfig() throws IOException {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        repoRoot = moduleDir.getParent();
        assertTrue(Files.isRegularFile(repoRoot.resolve("pom.xml")),
                "the contract test must run from an eladmin module directory, got " + moduleDir);
        try (InputStream in = Files.newInputStream(
                repoRoot.resolve("eladmin-system/src/main/resources/config/application.yml"))) {
            application = new Yaml().load(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> spring() {
        return (Map<String, Object>) application.get("spring");
    }

    @Test
    void oldSpringRedisNamespaceIsGone() {
        assertFalse(spring().containsKey("redis"),
                "spring.redis.* was removed in Boot 3 and silently unbinds on Boot 4; "
                        + "use spring.data.redis.* instead");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dataRedisNamespaceCarriesConnectionContract() {
        Map<String, Object> redis = (Map<String, Object>) spring().get("data");
        assertNotNull(redis, "spring.data.redis block must exist");
        Map<String, Object> dataRedis = (Map<String, Object>) redis.get("redis");
        assertNotNull(dataRedis, "spring.data.redis block must exist");
        assertEquals("${REDIS_DB:0}", dataRedis.get("database"));
        assertEquals("${REDIS_HOST:127.0.0.1}", dataRedis.get("host"));
        assertEquals("${REDIS_PORT:6379}", dataRedis.get("port"));
        assertEquals("${REDIS_PWD:}", dataRedis.get("password"));
        assertEquals(5000, dataRedis.get("timeout"));
        Map<String, Object> lettuce = (Map<String, Object>) dataRedis.get("lettuce");
        assertNotNull(lettuce, "lettuce pool block must survive the namespace move");
        Map<String, Object> pool = (Map<String, Object>) lettuce.get("pool");
        assertNotNull(pool);
        assertEquals(30, pool.get("max-active"));
        assertEquals(20, pool.get("max-idle"));
        assertEquals(1, pool.get("min-idle"));
    }

    @Test
    void redissonStarterAndConfigurationAreRemoved() throws IOException {
        String pom = Files.readString(repoRoot.resolve("pom.xml"), StandardCharsets.UTF_8);
        // 只审计依赖坐标本身，注释里出现 redisson 字样不算回归
        java.util.regex.Matcher redissonDependency = java.util.regex.Pattern
                .compile("(?s)<dependency>.*?(redisson).*?</dependency>")
                .matcher(pom);
        assertFalse(redissonDependency.find(),
                "the redisson starter is Boot 2 era, binary incompatible with Spring Data Redis 4, "
                        + "and its client bean has zero consumers");
        Path legacy = repoRoot.resolve(
                "eladmin-common/src/main/java/me/zhengjie/config/RedissonConfiguration.java");
        assertFalse(Files.exists(legacy),
                "RedissonConfiguration reads the removed spring.redis.* keys and must not survive");
    }
}
