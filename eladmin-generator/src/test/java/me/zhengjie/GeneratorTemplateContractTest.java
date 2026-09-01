/*
 *  Copyright 2019-2025 Zheng Jie
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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 代码生成器模板契约测试：admin 模板只允许 OpenAPI 3 注解，
 * 生成的 delete 方法保留完整描述且与 Spring 的 @RequestBody 不冲突。
 * 纯文本断言，不依赖数据库、Redis 或模板渲染运行时。
 *
 * @author Zheng Jie
 */
@org.junit.jupiter.api.Tag("static-contract")
class GeneratorTemplateContractTest {

    private static final Pattern LEGACY_SWAGGER = Pattern.compile(
            "io\\.swagger\\.annotations|@ApiOperation|@ApiModelProperty|@ApiParam|\\b@Api\\s*\\(");
    private static final Pattern QUALIFIED_REQUEST_BODY = Pattern.compile(
            "@io\\.swagger\\.v3\\.oas\\.annotations\\.parameters\\.RequestBody\\(description = \"[^\"]*\"\\)\\s*@RequestBody");
    private static final List<String> TEMPLATE_NAMES = List.of(
            "Controller.ftl", "Dto.ftl", "Entity.ftl", "QueryCriteria.ftl");

    private static Path templateDir;

    @BeforeAll
    static void locateTemplates() {
        Path moduleDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        templateDir = moduleDir.resolve("src/main/resources/template/admin");
        assertTrue(Files.isDirectory(templateDir),
                "generator admin template directory must exist at " + templateDir);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    void templatesContainNoLegacySwaggerAnnotations() throws IOException {
        for (String name : TEMPLATE_NAMES) {
            String template = read(templateDir.resolve(name));
            assertFalse(LEGACY_SWAGGER.matcher(template).find(),
                    name + " must not reference Swagger 1.x annotations");
        }
    }

    @Test
    void generatedControllerUsesOpenApiThreeOperationAndTag() throws IOException {
        String template = read(templateDir.resolve("Controller.ftl"));
        assertTrue(template.contains("import io.swagger.v3.oas.annotations.Operation;"));
        assertTrue(template.contains("import io.swagger.v3.oas.annotations.tags.Tag;"));
        assertTrue(template.contains("@Tag(name = \"${apiAlias}\")"),
                "Api tags must map to Tag name");
        assertTrue(template.contains("@Operation(summary = "),
                "ApiOperation value must map to Operation summary");
    }

    @Test
    void generatedDeleteKeepsRequestBodyDescriptionWithoutSpringCollision() throws IOException {
        String template = read(templateDir.resolve("Controller.ftl"));
        assertTrue(QUALIFIED_REQUEST_BODY.matcher(template).find(),
                "delete ids parameter must keep a fully qualified OpenAPI RequestBody description");
        assertFalse(template.contains("import io.swagger.v3.oas.annotations.parameters.RequestBody;"),
                "the OpenAPI RequestBody must stay fully qualified to avoid colliding with Spring's RequestBody");
    }

    @Test
    void generatedModelsUseSchemaDescription() throws IOException {
        for (String name : List.of("Dto.ftl", "Entity.ftl", "QueryCriteria.ftl")) {
            String template = read(templateDir.resolve(name));
            assertTrue(template.contains("import io.swagger.v3.oas.annotations.media.Schema;"),
                    name + " must import the OpenAPI 3 Schema annotation");
            assertTrue(template.contains("@Schema(description = "),
                    name + " must map ApiModelProperty value to Schema description");
        }
    }

    @Test
    void adminTemplateDirectoryHoldsExactlyTheExpectedTemplates() throws IOException {
        try (Stream<Path> files = Files.list(templateDir)) {
            List<String> actual = files.map(path -> path.getFileName().toString()).sorted().toList();
            assertEquals(List.of("Controller.ftl", "Dto.ftl", "Entity.ftl", "Mapper.ftl",
                    "QueryCriteria.ftl", "Repository.ftl", "Service.ftl", "ServiceImpl.ftl"), actual,
                    "an unreviewed template would bypass this contract");
        }
    }
}
