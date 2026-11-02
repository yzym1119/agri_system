package com.agri_system.infrastructure.common.config;

import com.agri_system.infrastructure.migration.LegacyPerformanceDropGuard;
import org.flywaydb.core.api.callback.Callback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring Boot 启动迁移使用同一历史保护；独立 Flyway 调用也必须显式注册此 callback。 */
@Configuration(proxyBeanMethods=false)
public class MigrationSafetyConfiguration {
    @Bean public Callback legacyPerformanceDropGuard() { return new LegacyPerformanceDropGuard(); }
}
