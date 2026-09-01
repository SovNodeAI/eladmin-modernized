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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 数据源运行时契约测试：主连接池继续由 Druid 提供，Boot 4 下必须改用
 * druid-spring-boot-4-starter（1.2.28，含 spring.boot4.autoconfigure 与
 * AutoConfiguration.imports 注册）。stat、wall、servlet 监控、连接池参数、
 * 生产环境占位符等配置契约全部保留，且不得回退 HikariCP 或引入 hikari 配置块。
 * 纯静态检查，不启动 Spring 上下文，不依赖 MySQL 或 Redis。
 *
 * @author SovNode
 */
@org.junit.jupiter.api.Tag("static-contract")
class DatasourceContractTest {

    private static final String BOOT4_AUTOCONFIGURE =
            "com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure";
    private static final String BOOT3_AUTOCONFIGURE =
            "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure";

    private static Path repoRoot;
    private static Map<String, Object> dev;
    private static Map<String, Object> prod;

    @BeforeAll
    static void loadRepo() throws IOException {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        repoRoot = moduleDir.getParent();
        assertTrue(Files.isRegularFile(repoRoot.resolve("pom.xml")),
                "the contract test must run from an eladmin module directory, got " + moduleDir);
        dev = loadProfile("application-dev.yml");
        prod = loadProfile("application-prod.yml");
    }

    private static Map<String, Object> loadProfile(String name) throws IOException {
        try (InputStream in = Files.newInputStream(repoRoot.resolve(
                "eladmin-system/src/main/resources/config/").resolve(name))) {
            return new Yaml().load(in);
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> druid(Map<String, Object> profile) {
        Map<String, Object> spring = (Map<String, Object>) profile.get("spring");
        assertNotNull(spring, "spring block must exist");
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        assertNotNull(datasource, "spring.datasource block must exist");
        assertFalse(datasource.containsKey("hikari"),
                "the primary pool stays on Druid, no hikari block may be introduced");
        Map<String, Object> druid = (Map<String, Object>) datasource.get("druid");
        assertNotNull(druid, "spring.datasource.druid block must be preserved");
        return druid;
    }

    @Test
    void rootPomSelectsTheBoot4DruidStarter() throws IOException {
        String pom = read(repoRoot.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>druid-spring-boot-4-starter</artifactId>"),
                "the primary datasource starter must be druid-spring-boot-4-starter");
        assertFalse(pom.contains("<artifactId>druid-spring-boot-3-starter</artifactId>"),
                "the Boot 3 starter does not register under Spring Boot 4 autoconfiguration");
        assertTrue(pom.contains("<druid.version>1.2.28</druid.version>"),
                "druid.version must stay pinned to the Boot 4 starter line 1.2.28");
        assertFalse(pom.contains("<artifactId>HikariCP</artifactId>"),
                "the pool must not be swapped to an explicit HikariCP dependency");
    }

    @Test
    void onlyTheBoot4DruidAutoConfigurationIsOnTheTestClasspath() {
        try {
            Class.forName(BOOT4_AUTOCONFIGURE, false, DatasourceContractTest.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            fail("the Boot 4 Druid autoconfigure classes must resolve from the starter");
        }
        try {
            Class.forName(BOOT3_AUTOCONFIGURE, false, DatasourceContractTest.class.getClassLoader());
            fail("the Boot 3 Druid starter must not remain anywhere on the classpath");
        } catch (ClassNotFoundException expected) {
            // Boot 3 starter is gone, exactly one Druid starter remains
        }
    }

    @Test
    void removeDruidAdConfigFollowsTheBoot4Package() throws IOException {
        Path config = repoRoot.resolve(
                "eladmin-common/src/main/java/me/zhengjie/config/RemoveDruidAdConfig.java");
        assertTrue(Files.isRegularFile(config),
                "RemoveDruidAdConfig keeps removing the monitoring console ads");
        String text = read(config);
        assertTrue(text.contains("import com.alibaba.druid.spring.boot4.autoconfigure.DruidDataSourceAutoConfigure;"),
                "the ad remover must be wired after the Boot 4 autoconfigure class");
        assertTrue(text.contains("import com.alibaba.druid.spring.boot4.autoconfigure.properties.DruidStatProperties;"),
                "the ad remover must bind the Boot 4 stat properties");
        assertFalse(text.contains("spring.boot3.autoconfigure"),
                "Boot 3 autoconfigure imports must not survive the starter switch");
    }

    @Test
    void profilesPreservePoolSizingAndMonitoringContract() {
        assertPoolContract(dev);
        assertPoolContract(prod);
    }

    private static void assertPoolContract(Map<String, Object> profile) {
        Map<String, Object> druid = druid(profile);
        assertEquals("com.alibaba.druid.pool.DruidDataSource", druid.get("db-type"));
        assertEquals(20, druid.get("initial-size"));
        assertEquals(20, druid.get("min-idle"));
        assertEquals(50, druid.get("max-active"));
        assertEquals(3000, druid.get("max-wait"));
        assertEquals(true, druid.get("keep-alive"));
        assertEquals("SELECT 1", druid.get("validation-query"));

        @SuppressWarnings("unchecked")
        Map<String, Object> webStatFilter = (Map<String, Object>) druid.get("webStatFilter");
        assertNotNull(webStatFilter, "webStatFilter monitoring must stay enabled");
        assertEquals(true, webStatFilter.get("enabled"));

        @SuppressWarnings("unchecked")
        Map<String, Object> statViewServlet = (Map<String, Object>) druid.get("stat-view-servlet");
        assertNotNull(statViewServlet, "stat-view-servlet console must stay configured");
        assertEquals("${DRUID_CONSOLE_ENABLED:false}", statViewServlet.get("enabled"));
        assertEquals("/druid/*", statViewServlet.get("url-pattern"));
        assertEquals("${DRUID_CONSOLE_ALLOW:127.0.0.1}", statViewServlet.get("allow"));
        assertEquals("${DRUID_CONSOLE_USER:}", statViewServlet.get("login-username"));
        assertEquals("${DRUID_CONSOLE_PWD:}", statViewServlet.get("login-password"));

        @SuppressWarnings("unchecked")
        Map<String, Object> filter = (Map<String, Object>) druid.get("filter");
        assertNotNull(filter, "filter block must stay");
        @SuppressWarnings("unchecked")
        Map<String, Object> stat = (Map<String, Object>) filter.get("stat");
        assertNotNull(stat, "stat filter must stay enabled with slow SQL tracking");
        assertEquals(true, stat.get("enabled"));
        assertEquals(true, stat.get("log-slow-sql"));
        assertEquals(2000, stat.get("slow-sql-millis"));
        assertEquals(true, stat.get("merge-sql"));
        @SuppressWarnings("unchecked")
        Map<String, Object> wall = (Map<String, Object>) filter.get("wall");
        assertNotNull(wall, "wall filter must stay");
        @SuppressWarnings("unchecked")
        Map<String, Object> wallConfig = (Map<String, Object>) wall.get("config");
        assertEquals(true, wallConfig.get("multi-statement-allow"));
    }

    @Test
    void driverStrategyStaysPerProfile() {
        assertEquals("com.p6spy.engine.spy.P6SpyDriver", druid(dev).get("driverClassName"));
        assertEquals("com.mysql.cj.jdbc.Driver", druid(prod).get("driverClassName"));
    }

    @Test
    void profileUrlsPinTheConnectorTimeZone() {
        assertUrlContract(dev);
        assertUrlContract(prod);
    }

    private static void assertUrlContract(Map<String, Object> profile) {
        String url = (String) druid(profile).get("url");
        assertNotNull(url, "the druid url must stay defined per profile");
        assertTrue(url.contains("connectionTimeZone=Asia/Shanghai"),
                "Connector/J 9 must pin the conversion zone via connectionTimeZone: " + url);
        assertFalse(url.contains("serverTimezone="),
                "serverTimezone still works as an alias of connectionTimeZone since 8.0.23, "
                        + "but the canonical property reads clearer and must be the only one carried: " + url);
        assertFalse(url.contains("forceConnectionTimeZoneToSession"),
                "forceConnectionTimeZoneToSession breaks connections on MySQL without time zone tables: " + url);
    }

    @Test
    void prodProfileKeepsEnvironmentPlaceholders() {
        Map<String, Object> druid = druid(prod);
        assertEquals(
                "jdbc:mysql://${DB_HOST}:${DB_PORT:3306}/${DB_NAME}"
                        + "?connectionTimeZone=Asia/Shanghai&characterEncoding=utf8&useSSL=false",
                druid.get("url"));
        assertEquals("${DB_USER}", druid.get("username"));
        assertEquals("${DB_PWD}", druid.get("password"));
    }

    @Test
    void prodProfileFailsClosedOnAuthenticationInputs() throws IOException {
        String prodText = read(repoRoot.resolve(
                "eladmin-system/src/main/resources/config/application-prod.yml"));
        assertTrue(prodText.contains("password: ${REDIS_PWD}"),
                "prod must override the dev oriented empty Redis password default with a required placeholder");
        assertFalse(prodText.contains("${REDIS_PWD:}"),
                "prod must not accept an empty Redis password fallback");
        assertFalse(prodText.contains("${DB_USER:root}"),
                "prod must not carry the root demo username fallback");
        assertFalse(prodText.contains("${DB_PWD:}"),
                "prod must not carry an empty DB password fallback");
        assertFalse(prodText.contains("${DB_HOST:localhost}"),
                "prod must not carry the localhost demo host fallback");
        assertFalse(prodText.contains("${DB_NAME:eladmin}"),
                "prod must not carry the eladmin demo database name fallback");
    }

    @Test
    void packagedLaunchRequiresAnExplicitProfileSelection() throws IOException {
        String baseText = read(repoRoot.resolve(
                "eladmin-system/src/main/resources/config/application.yml"));
        assertTrue(baseText.contains("active: ${SPRING_PROFILES_ACTIVE:}"),
                "the base yml must resolve the active profile from the environment only");
        assertFalse(baseText.contains("active: dev"),
                "a packaged launch must not silently select dev");
        String devText = read(repoRoot.resolve(
                "eladmin-system/src/main/resources/config/application-dev.yml"));
        assertTrue(devText.contains("username: ${DB_USER:root}"),
                "the dev profile keeps its convenience username default");
        assertTrue(devText.contains("password: ${DB_PWD:}"),
                "the dev profile keeps its convenience empty password default");
    }
}
