/*
 *  Modified by SovNode in 2026 (Spring Boot 4.1 modernization); see NOTICE.
 */
package me.zhengjie.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * @author Zheng Jie
 * @date 2025-06-25
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "amz.s3")
public class AmzS3Config {

    /**
     * S3 对象存储的显式启用开关（SOV-1037 显式契约）：
     * 默认 false，未配置 S3 的部署可以正常启动（S3 客户端 bean 不创建）；
     * 显式置 true 时，凭据在启动期即被校验，任何必需输入为空都会在
     * 到达 "Started" 启动完成标记之前以输入名失败关闭（fail closed），
     * 绝不静默推迟一个配置错误的已启用集成。
     * 环境输入名为 S3_ENABLED（对应 amz.s3.enabled）。
     */
    private boolean enabled = false;

    /**
     * Amazon S3 的区域配置，例如 "us-west-2"。
     * 该区域决定了 S3 存储桶的地理位置。
     */
    private String region;

	/**
	 * Amazon S3 的端点 URL
	 * 该端点用于访问 S3 服务。
	 */
	private String endPoint;

	/**
	 * Amazon S3 的域名
	 * 该域名用于构建访问 S3 服务的完整 URL。
	 */
	private String domain;

	/**
	 * Amazon S3 的访问密钥 ID，用于身份验证。
	 * 该密钥与 secretKey 一起使用来授权对 S3 服务的访问。
	 */
	private String accessKey;

	/**
	 * Amazon S3 的秘密访问密钥，用于身份验证。
	 * 该密钥与 accessKey 一起使用来授权对 S3 服务的访问。
	 */
	private String secretKey;

	/**
	 * 默认的 S3 存储桶名称。
	 * 该存储桶用于存储上传的文件和数据。
	 */
	private String defaultBucket;

	/**
	 * 文件上传后存储的文件夹格式，默认为 "yyyy-MM"。
	 */
	private String timeformat;

	/**
	 * 创建并返回一个 AmazonS3 客户端实例。
	 * 使用当前配置类的 endPoint, region, accessKey 和 secretKey。
	 * 声明为 @Bean 后，Spring 会将其作为单例管理，并在需要时自动注入。
	 *
	 * 仅当 amz.s3.enabled=true（显式启用，环境输入 S3_ENABLED）时创建；
	 * 启用即校验：S3_ACCESS_KEY、S3_SECRET_KEY 及 region/endPoint 为空时，
	 * 在 bean 创建期以输入名抛出明确异常，启动在刷新阶段失败关闭，
	 * 早于 "Started AppRun in" 启动完成标记（SOV-1037 / SOV-1040 契约）。
	 * 凭据使用静态提供器即时解析，不做首次使用延迟校验。
	 *
	 * @return 配置好的 AmazonS3 客户端实例
	 */
	@Bean
	@ConditionalOnProperty(prefix = "amz.s3", name = "enabled", havingValue = "true")
	public S3Client amazonS3Client() {
		requireEnabledInput("amz.s3.accessKey", "S3_ACCESS_KEY", accessKey);
		requireEnabledInput("amz.s3.secretKey", "S3_SECRET_KEY", secretKey);
		requireEnabledInput("amz.s3.region", "S3_REGION", region);
		requireEnabledInput("amz.s3.endPoint", "S3_ENDPOINT", endPoint);
		return S3Client.builder().region(Region.of(region))
				.endpointOverride(URI.create(endPoint))
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				.build();
	}

	/**
	 * 已启用集成的启动期输入校验：空值直接以属性名与环境输入名失败关闭。
	 */
	private static void requireEnabledInput(String property, String envName, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("AmzS3Config: amz.s3.enabled=true (environment input S3_ENABLED) "
					+ "requires a nonblank " + property + " (environment input " + envName + "); "
					+ "startup refuses to continue with a misconfigured enabled S3 integration. "
					+ "Leave amz.s3.enabled=false for an S3-free deployment.");
		}
	}
}