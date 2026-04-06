package com.agri_system.infrastructure.auth.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token配置
 * 配置拦截器和路由规则
 * 
 * @author AI Generated
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    
    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册Sa-Token拦截器，校验规则在run方法中定义
        registry.addInterceptor(new SaInterceptor(handler -> {
            // 指定拦截的路由与放行的路由
            SaRouter.match("/**")
                    .notMatch("/auth/**")  // 放行登录接口
                    .notMatch("/api/public/**")
                    .notMatch("/api/client/public/**")  // 放行客户端公开接口
                    .notMatch("/swagger-ui/**")  // 放行Swagger
                    .notMatch("/api-docs/**")  // 放行API文档
                    .notMatch("/error")  // 放行错误页面
                    .notMatch(r -> {
                        // 放行 OPTIONS 预检请求（CORS）
                        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest request = attributes.getRequest();
                            return "OPTIONS".equalsIgnoreCase(request.getMethod());
                        }
                        return false;
                    })
                    .check(r -> {
                        // 先校验是否登录
                        StpUtil.checkLogin();
                        
                        // 获取当前请求路径
                        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                        if (attributes != null) {
                            HttpServletRequest request = attributes.getRequest();
                            String path = request.getRequestURI();
                            
                            // 获取当前登录ID
                            String loginId = StpUtil.getLoginIdAsString();
                            
                            // 管理端接口：校验STAFF_前缀
                            if (path.startsWith("/api/admin/")) {
                                if (!loginId.startsWith("STAFF_")) {
                                    throw new RuntimeException("管理端接口需要员工登录");
                                }
                            }
                            // 客户端接口：校验CUST_前缀
                            else if (path.startsWith("/api/client/")) {
                                if (!loginId.startsWith("CUST_")) {
                                    throw new RuntimeException("客户端接口需要客户登录");
                                }
                            }
                        }
                    });
        })).addPathPatterns("/**");
    }
}

