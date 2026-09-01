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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产 profile 的正向启动测试（SOV-1037）：显式激活 prod，并把契约要求的
 * 全部必要输入（数据源 URL/账号/口令、带口令的 Redis、JWT 密钥、RSA 私钥）
 * 通过 DynamicPropertySource 真实注入，断言完整应用上下文启动成功、
 * ApplicationReadyEvent（"started" 标记）确实到达、根端点返回启动签名、
 * prod 下 swagger 关闭，且带口令 Redis 可用生产 RedisTemplate 完成往返。
 *
 * 无 Docker 的环境该类整体诚实跳过（disabledWithoutDocker），CI 的
 * Docker 环境（.github/workflows/ci.yml）是它的执行路径。
 *
 * @author SovNode
 */
@Tag("behavioral")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProdProfileContextBootTests {

    /**
     * 记录 ApplicationReadyEvent（"started" 标记）是否到达的探针 bean；
     * Boot 4 已移除旧的 ApplicationEvents 测试工具，这里用事件监听直证。
     * 用 @TestConfiguration 避免嵌套配置类劫持 @SpringBootTest 的主配置探测。
     */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ReadyProbe {
        static final AtomicBoolean PROBE = new AtomicBoolean(false);

        @EventListener(ApplicationReadyEvent.class)
        public void onReady() {
            PROBE.set(true);
        }
    }

    private static final String REDIS_TEST_PASSWORD = "prod-boot-test-redis-password";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("eladmin")
            .withUsername("eladmin")
            .withPassword("eladmin")
            .withCopyFileToContainer(
                    org.testcontainers.utility.MountableFile.forHostPath(eladminSql(), 0644),
                    "/docker-entrypoint-initdb.d/eladmin.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine"))
            .withCommand("redis-server", "--requirepass", REDIS_TEST_PASSWORD)
            .withExposedPorts(6379);

    private static final String TEST_JWT_SECRET = generateTestJwtSecret();

    private static final String TEST_RSA_PRIVATE_KEY = generateTestRsaKey();

    private static String generateTestJwtSecret() {
        byte[] seed = new byte[64];
        new java.security.SecureRandom().nextBytes(seed);
        return java.util.Base64.getEncoder().encodeToString(seed);
    }

    private static String generateTestRsaKey() {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            byte[] der = generator.generateKeyPair().getPrivate().getEncoded();
            return java.util.Base64.getEncoder().encodeToString(der);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("unable to generate the test RSA key", e);
        }
    }

    @DynamicPropertySource
    static void productionContractConfiguration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
        registry.add("spring.datasource.druid.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("spring.data.redis.password", () -> REDIS_TEST_PASSWORD);
        registry.add("jwt.base64-secret", () -> TEST_JWT_SECRET);
        registry.add("rsa.private_key", () -> TEST_RSA_PRIVATE_KEY);
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private Environment environment;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Test
    void prodContextBootsWithAllRequiredInputsAndReachesTheStartedMarker() {
        assertTrue(environment.acceptsProfiles(
                        org.springframework.core.env.Profiles.of("prod")),
                "the prod profile must be the explicitly active profile");
        assertFalse(environment.acceptsProfiles(
                        org.springframework.core.env.Profiles.of("dev")),
                "the dev convenience defaults must stay out of a prod boot");
        assertTrue(ReadyProbe.PROBE.get(),
                "ApplicationReadyEvent (the started marker) must have been reached");
    }

    @Test
    void rootEndpointServesTheStartedSignatureOverHttp11() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + serverPort + "/"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> body = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, body.statusCode());
        // AppRun.index 返回 String，经 fastjson2 转换器以 JSON 字符串（带引号）落地
        assertEquals("\"Backend service started successfully\"", body.body());
    }

    @Test
    void swaggerUiStaysDisabledInProd() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + serverPort + "/doc.html"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> body = client.send(request, HttpResponse.BodyHandlers.ofString());
        // swagger.enabled=false（prod）下 UI 不可达。SOV-779 静态预期是 404，
        // 打包产物的真实运行时形状是 springdoc 的 400 拒绝；两者都证明界面未上线，
        // 这里固定实测值，若未来变成 200 则说明 prod 泄露了 swagger 界面。
        assertEquals(400, body.statusCode(),
                "swagger.enabled=false in prod must not serve the doc.html UI");
    }

    @Test
    void authenticatedRedisRoundTripsThroughTheProductionTemplate() {
        String key = "prod-boot:test:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(key, "prod-boot-redis-round-trip");
        Object read = redisTemplate.opsForValue().get(key);
        assertEquals("prod-boot-redis-round-trip", read);
    }

    private static Path eladminSql() {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = moduleDir.getParent();
        Path sql = repoRoot.resolve("sql/eladmin.sql");
        assertTrue(Files.isRegularFile(sql),
                "the project schema script must exist at " + sql + ", run from " + moduleDir);
        return sql;
    }
}
