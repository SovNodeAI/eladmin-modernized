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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布完整性源码审计测试：普通 mvn verify 必须默认执行测试，
 * skipTests 只能通过命令行 -DskipTests=true 显式关闭；任何模块不得
 * 通过 surefire 排除把测试挡在默认验证门之外。静态文本检查，属迁移脚手架。
 *
 * @author SovNode
 */
@org.junit.jupiter.api.Tag("static-contract")
class ReleaseIntegritySourceAuditTest {

    private static Path repoRoot;
    private static String rootPom;

    @BeforeAll
    static void locateRepoRoot() throws IOException {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        repoRoot = moduleDir.getParent();
        assertTrue(Files.isRegularFile(repoRoot.resolve("pom.xml")),
                "the audit test must run from an eladmin module directory, got " + moduleDir);
        rootPom = new String(Files.readAllBytes(repoRoot.resolve("pom.xml")), StandardCharsets.UTF_8);
    }

    @Test
    void rootPomDefaultsSkipTestsToFalse() {
        assertTrue(rootPom.contains("<skipTests>false</skipTests>"),
                "the root pom must default skipTests to false so plain mvn verify runs the tests");
        assertFalse(rootPom.contains("<skipTests>true</skipTests>"),
                "the root pom must not default skipTests to true; skipping tests stays an explicit "
                        + "command line opt out via -DskipTests=true");
    }

    @Test
    void noModuleHidesTestsBehindSurefireExclusions() throws IOException {
        // 曾经的漏洞：EladminSystemApplicationTests 被 surefire 排除在所有
        // Maven 阶段之外，而本审计类只盯根 pom 的 skipTests，结构上无法发现。
        // 现在容器化启动测试留在默认验证门里，任何模块再引入排除都会在这里变红。
        for (String module : new String[] {
                "eladmin-system/pom.xml", "eladmin-common/pom.xml",
                "eladmin-logging/pom.xml", "eladmin-generator/pom.xml",
                "eladmin-tools/pom.xml"}) {
            String pom = new String(Files.readAllBytes(repoRoot.resolve(module)),
                    StandardCharsets.UTF_8);
            assertFalse(pom.contains("<exclude>"),
                    module + " must not exclude any test from the default gate; "
                            + "tests that need external services must carry an honest "
                            + "runtime availability guard instead");
        }
    }

    @Test
    void commonsPool2StaysManagedByTheBootBom() throws IOException {
        int dependency = rootPom.indexOf("<artifactId>commons-pool2</artifactId>");
        assertTrue(dependency >= 0, "commons-pool2 must stay a declared dependency for the Redis pool");
        String block = rootPom.substring(dependency, rootPom.indexOf("</dependency>", dependency));
        assertFalse(block.contains("<version>"),
                "commons-pool2 must take its version from the Spring Boot BOM instead of a stale pin");
        assertFalse(rootPom.contains("<commons-pool2.version>"),
                "the stale commons-pool2.version property must stay removed");
    }

    @Test
    void noModulePomPinsTheDeadJnaProperty() throws IOException {
        String systemPom = new String(Files.readAllBytes(
                repoRoot.resolve("eladmin-system/pom.xml")), StandardCharsets.UTF_8);
        assertFalse(systemPom.contains("jna.version"),
                "the dead jna.version pin must stay removed; oshi manages its own JNA line");
    }

    @Test
    void jwtStackStaysOnTheModernJjwtGsonLine() throws IOException {
        String systemPom = new String(Files.readAllBytes(
                repoRoot.resolve("eladmin-system/pom.xml")), StandardCharsets.UTF_8);
        assertTrue(systemPom.contains("<jjwt.version>0.13.0</jjwt.version>"),
                "jjwt must stay on the 0.13.0 line");
        assertTrue(systemPom.contains("<artifactId>jjwt-gson</artifactId>"),
                "jjwt-gson must serialize JWT claims so JWT handling does not depend on the "
                        + "Jackson 2 stack that only the springdoc docs toolchain still carries");
        assertFalse(systemPom.contains("<artifactId>jjwt-jackson</artifactId>"),
                "jjwt-jackson drags a second Jackson stack beside the Boot 4 tools.jackson line");
    }

    @Test
    void ipResolverStaysOnTheJacksonThreeMicaLine() {
        int dependency = rootPom.indexOf("<artifactId>mica-ip2region</artifactId>");
        assertTrue(dependency >= 0, "mica-ip2region must stay declared for IP resolution");
        String block = rootPom.substring(dependency, rootPom.indexOf("</dependency>", dependency));
        assertTrue(block.contains("<version>4.1.0</version>"),
                "mica-ip2region must stay on the 4.1.0 line so mica-core binds tools.jackson "
                        + "instead of dragging Jackson 2 back into the runtime");
    }

    @Test
    void webStarterCarriesNoInertJsonExclusions() {
        int web = rootPom.indexOf("<artifactId>spring-boot-starter-web</artifactId>");
        assertTrue(web >= 0, "spring-boot-starter-web must stay declared");
        String block = rootPom.substring(web, rootPom.indexOf("</dependency>", web));
        assertFalse(block.contains("<exclusions>"),
                "the Boot 4 web starter must carry no json exclusions: starter-json still exists "
                        + "at 4.1.1, but the exclusion was inert because spring-boot-starter-web "
                        + "no longer depends on it; starter-jackson arrives via starter-webmvc "
                        + "anyway, so the removal was a cleanup, not a security lever");
    }
}
