package com.agri_system.infrastructure.storage.config;

import com.agri_system.domain.storage.FileStorageRepository;
import com.agri_system.infrastructure.storage.repository.AliyunOssFileStorageRepository;
import com.agri_system.infrastructure.storage.repository.LocalFileStorageRepository;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    /**
     * 存储实现统一在本配置类内按序注册：OSS 客户端存在 → 云存储；
     * 否则兜底本地存储。两个实现的 @Bean 条件在同一配置类内求值，
     * 顺序确定，避免 @Component 级 @ConditionalOnBean 对扫描顺序的依赖
     * （旧写法在大小写敏感文件系统上曾导致 CI 中两个实现双双未注册）。
     */
    @Bean
    @ConditionalOnBean(OSS.class)
    public FileStorageRepository fileStorageRepository(OSS ossClient, AliyunOssProperties properties) {
        return new AliyunOssFileStorageRepository(ossClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean(FileStorageRepository.class)
    public FileStorageRepository localFileStorageRepository() {
        return new LocalFileStorageRepository();
    }
}
