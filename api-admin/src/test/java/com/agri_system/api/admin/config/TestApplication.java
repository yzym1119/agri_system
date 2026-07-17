package com.agri_system.api.admin.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试专用 Spring Boot 配置类
 * 不扫描 controller 包（由 @ContextConfiguration 显式指定），
 * 不包含 @MapperScan，避免 WebMvcTest 加载 MyBatis mapper 时报错
 */
@SpringBootApplication(scanBasePackages = "com.agri_system.api.admin.config")
public class TestApplication {
}
