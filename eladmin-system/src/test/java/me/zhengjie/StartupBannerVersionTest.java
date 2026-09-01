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
package me.zhengjie;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动横幅版本回归测试：仓库自带的 banner.txt 曾经硬编码上游 eladmin 的
 * Spring Boot v2.6.4 字样，而本分支实际解析的运行时是 Boot 4.1.1，
 * 启动输出因此在说假话。该字样已改为 ${spring-boot.version} 占位符，
 * ResourceBanner 会在启动时用真实解析的 Boot 版本渲染它。
 *
 * 本测试用最小上下文真实启动一次应用（不加载自动配置，不需要数据库），
 * 捕获控制台输出，断言横幅打印的是运行时解析出的版本而不是任何硬编码
 * 字面量。若有人把死版本号写回 banner.txt，此测试会变红。上下文在
 * try with resources 中关闭，且整个启动都包在外层 finally 里，即使
 * 启动抛出异常也必然恢复标准输出流；关闭本身由 AutoCloseable 保证，
 * 只保留真正可能失败的断言。
 *
 * @author SovNode
 */
@Tag("behavioral")
class StartupBannerVersionTest {

    @Test
    void bannerPrintsTheResolvedSpringBootVersion() {
        String resolved = SpringBootVersion.getVersion();
        assertNotNull(resolved, "SpringBootVersion must resolve from the spring-boot jar manifest");
        assertFalse(resolved.isBlank(), "resolved Boot version must not be blank");

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfig.class)
                    .web(WebApplicationType.NONE)
                    .bannerMode(Banner.Mode.CONSOLE)
                    .logStartupInfo(false)
                    .run()) {
                assertTrue(context.isActive(), "the minimal context must be active when the banner is assessed");
            }
        } finally {
            System.setOut(originalOut);
        }
        String console = captured.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains(":: Spring Boot ::"),
                "the classpath banner.txt must be printed, got:\n" + console);
        assertTrue(console.contains("(v" + resolved + ")"),
                "banner must print the resolved Boot version " + resolved + ", got:\n" + console);
        assertFalse(console.contains("2.6.4"),
                "banner must not print the stale hard coded upstream version");
    }

    @Configuration
    static class EmptyConfig {
    }
}
