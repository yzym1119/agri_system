package com.agri_system.api.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class PrintWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // serve /prints/** from filesystem prints directory under project root
        String printsPath = new File("prints").getAbsolutePath();
        registry.addResourceHandler("/prints/**")
                .addResourceLocations("file:" + printsPath + File.separator);
    }
}











