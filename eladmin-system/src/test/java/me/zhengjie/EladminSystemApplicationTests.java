/*
 *  Copyright 2019-2025 Zheng Jie
 *  Modified by SovNode in 2026 (Spring Boot 4.1 modernization); see NOTICE.
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

import lombok.Data;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认验证门里的上下文启动测试：用 MySQL 与 Redis Testcontainers 提供真实服务，
 * 以仓库自带 sql/eladmin.sql 初始化库，显式激活 dev profile（打包产物不再
 * 默认选中 dev），断言完整应用上下文可以启动、
 * /v3/api/docs 通过真实注册的 HTTP 转换器返回 OpenAPI JSON、
 * 生产 RedisTemplate 能做值往返。无 Docker 的环境下整个类诚实跳过
 * （disabledWithoutDocker），不再依赖 surefire 排除。
 *
 * Druid starter 读取 spring.datasource.druid.* 命名空间，@ServiceConnection
 * 只覆盖 spring.datasource.*，因此连接细节通过 DynamicPropertySource 直接注入。
 *
 * @author Zheng Jie
 */
@Tag("behavioral")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class EladminSystemApplicationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("eladmin")
            .withUsername("eladmin")
            .withPassword("eladmin")
            .withCopyFileToContainer(
                    // 显式 0644：Testcontainers 会保留宿主文件模式，umask 077 的检出
                    // 会让容器内 mysql 用户读不了初始化脚本（Permission denied）
                    org.testcontainers.utility.MountableFile.forHostPath(eladminSql(), 0644),
                    "/docker-entrypoint-initdb.d/eladmin.sql");

    @Container
    static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>(
                    org.testcontainers.utility.DockerImageName.parse("redis:7.2-alpine"))
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
    static void containerBackedConfiguration(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
        registry.add("spring.datasource.druid.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("jwt.base64-secret", () -> TEST_JWT_SECRET);
        registry.add("rsa.private_key", () -> TEST_RSA_PRIVATE_KEY);
    }

    @LocalServerPort
    private int serverPort;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void openApiJsonIsServedThroughTheRegisteredConverter() throws Exception {
        // HTTP/1.1 显式协商：JDK HttpClient 走 h2 升级时会被 Tomcat 的
        // overhead 保护以 RST_STREAM 拒绝，与被测的 JSON 内容无关
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + serverPort + "/v3/api-docs"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> docs = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, docs.statusCode());
        String contentType = docs.headers().firstValue("Content-Type").orElse("");
        assertTrue(MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON),
                "api docs must come back as JSON, got: " + contentType);
        String body = docs.body();
        assertNotNull(body);
        assertTrue(body.contains("\"openapi\""), "OpenAPI version key must be present");
        assertTrue(body.contains("\"paths\""), "paths block must be present");
        assertTrue(body.contains("\"info\""), "info block must be present");
    }

    @Test
    void redisValueRoundTripsThroughTheProductionTemplate() {
        Payload payload = new Payload();
        payload.setId(41L);
        payload.setName("boot切片往返值");
        payload.setEnabled(Boolean.TRUE);
        payload.setCreateTime(new Date(1_700_001_111_000L));

        String key = "boot:test:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(key, payload);
        Object read = redisTemplate.opsForValue().get(key);

        Payload back = assertInstanceOf(Payload.class, read);
        assertEquals(Long.valueOf(41L), back.getId());
        assertEquals("boot切片往返值", back.getName());
        assertEquals(Boolean.TRUE, back.getEnabled());
        assertEquals(new Date(1_700_001_111_000L), back.getCreateTime());
    }

    private static Path eladminSql() {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path repoRoot = moduleDir.getParent();
        Path sql = repoRoot.resolve("sql/eladmin.sql");
        assertTrue(Files.isRegularFile(sql),
                "the project schema script must exist at " + sql + ", run from " + moduleDir);
        return sql;
    }

    @Data
    public static class Payload {
        private Long id;
        private String name;
        private Boolean enabled;
        private Date createTime;
    }
}
