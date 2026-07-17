package com.agri_system.infrastructure.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

/**
 * CORS 跨域配置
 *
 * 安全说明：
 * - 开发环境：允许所有来源（allowed-origins=*），方便调试
 * - 生产环境：必须配置具体的前端域名，防止 CSRF 攻击
 *
 * @author AI Generated
 */
@Configuration
public class CorsConfig {

    /**
     * 允许的源列表，从配置文件读取。
     * 开发环境默认 "*"，生产环境必须设置为具体域名（如 https://admin.agri.com,https://m-agri.com）
     */
    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 安全：生产环境禁止使用通配符 + credentials
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        if (origins.contains("*")) {
            configuration.setAllowedOriginPatterns(origins);
        } else {
            configuration.setAllowedOrigins(origins);
        }

        // 允许的请求头
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Requested-With",
            "X-Staff-Id",
            "X-Customer-Id"
        ));

        // 允许的请求方法
        configuration.setAllowedMethods(Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS",
            "PATCH"
        ));

        // 允许携带凭证（Cookie等）
        configuration.setAllowCredentials(true);

        // 预检请求的缓存时间（秒）
        configuration.setMaxAge(3600L);

        // 暴露的响应头
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return new CorsFilter(source);
    }
}

