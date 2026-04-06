package com.agri_system.infrastructure.common.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cache 配置
 * 使用内存缓存（ConcurrentMapCacheManager）
 * 如果需要使用 Redis 缓存，可以替换为 RedisCacheManager
 * 
 * @author AI Generated
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        // 预定义缓存名称
        cacheManager.setCacheNames(java.util.Arrays.asList("sys_dict"));
        return cacheManager;
    }
}






