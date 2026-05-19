package com.epsilon.welink.common.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 项目启动时，创建一个 MinioClient 客户端实例，交给 Spring 管理，
// 随时可以上传 / 下载 / 删除文件（聊天图片、头像、文件等）
@Configuration
public class MinioConfig {

    // 服务地址
    @Value("${welink.minio.endpoint}")
    private String endpoint;

    // 访问密钥
    @Value("${welink.minio.access-key}")
    private String accessKey;

    // 密钥
    @Value("${welink.minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
