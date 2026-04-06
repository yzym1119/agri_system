package com.agri_system.application.performance.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PerformanceProperties.class)
public class PerformanceAutoConfiguration {
}

