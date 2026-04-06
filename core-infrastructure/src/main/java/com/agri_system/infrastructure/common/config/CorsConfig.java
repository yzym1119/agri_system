package com.agri_system.infrastructure.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * CORS 跨域配置
 * 
 * @author AI Generated
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 允许的源（开发环境允许所有，生产环境需要指定具体域名）
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        
        // 允许的请求头
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "satoken",
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
            "Authorization",
            "satoken"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return new CorsFilter(source);
    }
}

