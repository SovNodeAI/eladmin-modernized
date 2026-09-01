/*
 *  Copyright 2026 SovNode
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
package me.zhengjie.generator;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAPI 3 迁移静态契约测试：对仓库源码、生成器模板与根 pom 做纯文本审计。
 * 本测试不启动 Spring 上下文、不执行任何运行时行为；它是迁移脚手架，
 * 计划在迁移验收后按计划清理。此前 system 模块存在近乎重复的
 * OpenApiMigrationSourceAuditTest，已删除合并到本类。
 *
 * @author SovNode
 */
@Tag("static-contract")
class OpenApiMigrationContractTest {

    private static final int EXPECTED_TAGS = 26;
    private static final int EXPECTED_OPERATIONS = 141;
    private static final int EXPECTED_SCHEMAS = 414;
    private static final int EXPECTED_HIDDEN = 3;

    private static Path repoRoot() {
        Path moduleDir = Path.of("").toAbsolutePath().normalize();
        Path root = moduleDir.getParent();
        assertTrue(root != null && Files.isRegularFile(root.resolve("pom.xml")),
                "repository root with pom.xml not found one level above the module");
        assertTrue(Files.isRegularFile(root.resolve("eladmin-system/pom.xml")),
                "repository root must contain the eladmin-system module");
        return root;
    }

    private static List<Path> mainSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java")
                            || p.getFileName().toString().endsWith(".ftl"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .forEach(sources::add);
        }
        return sources;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void noLegacySwaggerAnnotationsRemainInSourcesOrTemplates() throws IOException {
        Path root = repoRoot();
        Pattern legacy = Pattern.compile("io\\.swagger\\.annotations|@Api\\b|@ApiOperation\\b|@ApiModelProperty\\b|@ApiParam\\b");
        List<String> offenders = new ArrayList<>();
        for (Path source : mainSources(root)) {
            if (legacy.matcher(read(source)).find()) {
                offenders.add(root.relativize(source).toString().replace('\\', '/'));
            }
        }
        assertEquals(List.of(), offenders, "legacy Swagger 1.x annotations remain");
    }

    @Test
    void v3AnnotationCountsMatchTheMigrationPacket() throws IOException {
        Path root = repoRoot();
        StringBuilder all = new StringBuilder();
        for (Path source : mainSources(root)) {
            all.append(read(source)).append('\n');
        }
        assertEquals(EXPECTED_TAGS, countOccurrences(Pattern.compile("(?m)^\\s*@Tag\\("), all.toString()));
        assertEquals(EXPECTED_OPERATIONS, countOccurrences(Pattern.compile("(?m)^\\s*@Operation\\("), all.toString()));
        assertEquals(EXPECTED_SCHEMAS, countOccurrences(Pattern.compile("(?m)^\\s*@Schema\\("), all.toString()));
        assertEquals(EXPECTED_HIDDEN, countOccurrences(Pattern.compile("(?m)^\\s*@Hidden\\b"), all.toString()));
    }

    @Test
    void appRunClassIsHiddenAndDeployKeepsRenamedHiddenSchema() throws IOException {
        Path root = repoRoot();
        String appRun = read(root.resolve("eladmin-system/src/main/java/me/zhengjie/AppRun.java"));
        assertTrue(appRun.contains("import io.swagger.v3.oas.annotations.Hidden;"));
        assertTrue(appRun.contains("@Hidden\n@SpringBootApplication") || appRun.replace("\r\n", "\n").contains("@Hidden\n@SpringBootApplication"),
                "AppRun must hide the application controller");
        String deploy = read(root.resolve("eladmin-system/src/main/java/me/zhengjie/modules/maint/domain/Deploy.java"));
        assertTrue(deploy.contains("@Schema(name = \"服务器\", hidden = true)"),
                "Deploy must keep the renamed hidden schema property");
    }

    @Test
    void generatedControllerTemplateUsesV3AnnotationsAndQualifiedRequestBody() throws IOException {
        Path root = repoRoot();
        String controller = read(root.resolve("eladmin-generator/src/main/resources/template/admin/Controller.ftl"));
        assertFalse(controller.contains("io.swagger.annotations"), "template must not import 1.x annotations");
        assertFalse(controller.contains("@ApiParam"), "template must not use @ApiParam");
        assertTrue(controller.contains("@Tag(name = \"${apiAlias}\")"));
        assertEquals(5, countOccurrences(Pattern.compile("@Operation\\(summary = "), controller));
        assertTrue(controller.contains("@io.swagger.v3.oas.annotations.parameters.RequestBody(description = \"传ID数组[]\")"),
                "generated delete endpoint must keep the request body description via the qualified v3 annotation");
        for (String template : List.of("Controller.ftl", "Dto.ftl", "Entity.ftl", "QueryCriteria.ftl")) {
            String text = read(root.resolve("eladmin-generator/src/main/resources/template/admin/" + template));
            assertFalse(text.contains("io.swagger.annotations"), template + " must not reference 1.x annotations");
        }
    }

    @Test
    void rootPomPinsSpringdocBoot4LineWithoutLegacyAnnotationsDependency() throws IOException {
        Path root = repoRoot();
        String pom = read(root.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>"));
        // 只审计 springdoc 依赖块自身的版本元素，避免与仓库中任何无关的 2.3.0 字符串冲突
        int springdoc = pom.indexOf("<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>");
        String springdocBlock = pom.substring(springdoc,
                pom.indexOf("</dependency>", springdoc));
        assertTrue(Pattern.compile("<version>3\\.1\\.0</version>")
                        .matcher(springdocBlock).find(),
                "springdoc must stay on the Boot 4 compatible 3.1.0 line");
        assertFalse(pom.contains("<groupId>io.swagger</groupId>"), "legacy swagger-annotations dependency must be gone");
        assertFalse(pom.contains("<artifactId>swagger-annotations</artifactId>"));
    }

    @Test
    void securityWhitelistCoversSpringdocEndpoints() throws IOException {
        Path root = repoRoot();
        String config = read(root.resolve(
                "eladmin-system/src/main/java/me/zhengjie/modules/security/config/SpringSecurityConfig.java"));
        for (String route : List.of("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/doc.html", "/webjars/**")) {
            assertTrue(config.contains("\"" + route + "\""), "whitelist must cover " + route);
        }
    }

    @Test
    void applicationConfigKeepsSpringdocEntryPoints() throws IOException {
        Path root = repoRoot();
        String application = read(root.resolve(
                "eladmin-system/src/main/resources/config/application.yml"));
        assertTrue(application.contains("springdoc:"), "springdoc configuration block must remain");
        assertTrue(application.contains("path: /doc.html"), "Knife4j entry point /doc.html must remain");
    }
}
