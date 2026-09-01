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

import me.zhengjie.exception.BadRequestException;
import me.zhengjie.repository.S3StorageRepository;
import me.zhengjie.rest.S3StorageController;
import me.zhengjie.service.impl.S3StorageServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S3 显式 opt-in 契约的三态矩阵（SOV-1037，回应 SOV-1040/SOV-1052 证据）：
 *
 * 1. disabled（默认）：不设置任何 S3 输入的上下文正常刷新，
 *    S3Client bean 不存在 —— S3-free 部署可以启动（SOV-1052 三个上下文
 *    测试在无 S3 环境变量下的干净通过路径）；
 * 2. enabled + 齐全输入：S3Client bean 存在（构造期不发起网络连接）；
 * 3. enabled + 缺失/为空输入：上下文刷新失败，失败消息按输入名点名
 *    （S3_ACCESS_KEY / S3_SECRET_KEY），即 SOV-1040 独立证明的
 *    “缺失 S3 输入阻止生产启动”在启用态被保留。
 *
 * 另固定 disabled 态的确定性文档化结果：控制器 S3 写路径返回 503 与固定
 * 消息；服务层首次使用抛同消息的 BadRequestException。
 */
@Tag("behavioral")
class AmzS3OptInContractTest {

    private static final String SYNTHETIC_ACCESS_KEY = "test-access-key";
    private static final String SYNTHETIC_SECRET_KEY = "test-secret-key";

    /**
     * 通过 @EnableConfigurationProperties 注册 AmzS3Config 及其绑定基础设施，
     * 使 amz.s3.* 属性真实绑定到字段（与生产上下文的绑定语义一致）。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AmzS3Config.class)
    static class BindingConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfig.class)
            .withPropertyValues(
                    "amz.s3.region=test",
                    "amz.s3.endPoint=https://s3.test.com",
                    "amz.s3.domain=https://s3.test.com",
                    "amz.s3.defaultBucket=test-bucket",
                    "amz.s3.timeformat=yyyy-MM");

    @Test
    void disabledByDefaultBootsWithoutAnyS3Input() {
        runner.run(context -> {
            assertTrue(context.isRunning(), "an S3-free deployment must boot with no S3 inputs at all");
            assertNull(context.getBeanProvider(S3Client.class).getIfAvailable(),
                    "no S3Client bean may exist when amz.s3.enabled is unset");
            assertFalse(context.getBean(AmzS3Config.class).isEnabled());
        });
    }

    @Test
    void explicitFalseAlsoBootsWithoutAnyS3Input() {
        runner.withPropertyValues("amz.s3.enabled=false").run(context -> {
            assertTrue(context.isRunning());
            assertNull(context.getBeanProvider(S3Client.class).getIfAvailable());
        });
    }

    @Test
    void enabledWithAllInputsCreatesTheS3Client() {
        runner.withPropertyValues(
                        "amz.s3.enabled=true",
                        "amz.s3.accessKey=" + SYNTHETIC_ACCESS_KEY,
                        "amz.s3.secretKey=" + SYNTHETIC_SECRET_KEY)
                .run(context -> {
                    assertTrue(context.isRunning(), "enabled with complete inputs must boot");
                    assertEquals(1, context.getBeansOfType(S3Client.class).size());
                });
    }

    @Test
    void enabledWithMissingAccessKeyFailsClosedNamingTheInput() {
        runner.withPropertyValues(
                        "amz.s3.enabled=true",
                        "amz.s3.secretKey=" + SYNTHETIC_SECRET_KEY)
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null,
                            "a misconfigured enabled integration must stop startup");
                    String chain = flatten(context.getStartupFailure());
                    assertTrue(chain.contains("amz.s3.enabled=true"),
                            "the failure must name the enabled contract, got: " + chain);
                    assertTrue(chain.contains("amz.s3.accessKey"),
                            "the failure must name the missing property, got: " + chain);
                    assertTrue(chain.contains("S3_ACCESS_KEY"),
                            "the failure must name the environment input, got: " + chain);
                });
    }

    @Test
    void enabledWithBlankSecretKeyFailsClosedNamingTheInput() {
        runner.withPropertyValues(
                        "amz.s3.enabled=true",
                        "amz.s3.accessKey=" + SYNTHETIC_ACCESS_KEY,
                        "amz.s3.secretKey=")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null,
                            "a blank secret on an enabled integration must stop startup");
                    String chain = flatten(context.getStartupFailure());
                    assertTrue(chain.contains("amz.s3.secretKey"),
                            "the failure must name the missing property, got: " + chain);
                    assertTrue(chain.contains("S3_SECRET_KEY"),
                            "the failure must name the environment input, got: " + chain);
                });
    }

    @Test
    void disabledControllerWritePathsReturnTheDocumentedServiceUnavailableOutcome() {
        AmzS3Config disabled = new AmzS3Config();
        disabled.setEnabled(false);
        disabled.setDomain("https://s3.test.com");
        S3StorageServiceImpl service = mock(S3StorageServiceImpl.class);
        S3StorageController controller = new S3StorageController(disabled, service);

        ResponseEntity<Object> upload = controller.uploadS3Storage(
                new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2, 3}));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, upload.getStatusCode(),
                "upload must return the documented deterministic disabled outcome");
        assertEquals(S3StorageServiceImpl.S3_STORAGE_DISABLED_MESSAGE,
                ((Map<?, ?>) upload.getBody()).get("message"));

        ResponseEntity<Object> delete = controller.deleteAllS3Storage(List.of(7L));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, delete.getStatusCode(),
                "delete must return the documented deterministic disabled outcome");
        assertEquals(S3StorageServiceImpl.S3_STORAGE_DISABLED_MESSAGE,
                ((Map<?, ?>) delete.getBody()).get("message"));
    }

    @Test
    void disabledServiceFirstUseFailsWithTheSameDeterministicMessage() {
        @SuppressWarnings("unchecked")
        ObjectProvider<S3Client> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        S3StorageServiceImpl service = new S3StorageServiceImpl(
                emptyProvider, new AmzS3Config(), mock(S3StorageRepository.class));

        BadRequestException upload = assertThrows(BadRequestException.class,
                () -> service.upload(new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1})));
        assertEquals(S3StorageServiceImpl.S3_STORAGE_DISABLED_MESSAGE, upload.getMessage());

        BadRequestException delete = assertThrows(BadRequestException.class,
                () -> service.deleteAll(List.of(7L)));
        assertEquals(S3StorageServiceImpl.S3_STORAGE_DISABLED_MESSAGE, delete.getMessage());
    }

    private static String flatten(Throwable head) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = head; t != null; t = t.getCause()) {
            chain.append(String.valueOf(t.getMessage())).append(" | ");
        }
        return chain.toString();
    }
}
