package com.agri_system.infrastructure.storage.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AliyunOssProperties.class)
public class AliyunOssConfig {

    /**
     * 仅在配置了非空 OSS 密钥时才创建客户端 Bean。
     * 未配置时使用本地文件存储（LocalFileStorageRepository）。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnExpression("!'${aliyun.oss.access-key-id:}'.isEmpty()")
    public OSS ossClient(AliyunOssProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }
}
