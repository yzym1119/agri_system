package com.agri_system.infrastructure.auth.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SaTokenConfigure implements WebMvcConfigurer {
    private final StaffRepository staffRepository;
    
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
                    .notMatch("/swagger-ui/**")  // 放行Swagger
                    .notMatch("/api-docs/**")  // 放行API文档
                    .notMatch("/error")  // 放行错误页面
                    .notMatch("/health")  // 放行健康检查（监控探针/负载均衡）
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
                                    throw NotLoginException.newInstance(
                                            "STAFF", NotLoginException.KICK_OUT, "管理端接口需要员工登录", null);
                                }
                                try {
                                    var staff = staffRepository.findIdentityById(Long.parseLong(loginId.substring(6)))
                                            .orElse(null);
                                    if (staff == null || !staff.isActive() || staff.hasBeenDeleted()) {
                                        throw NotLoginException.newInstance("STAFF", NotLoginException.KICK_OUT,
                                                "员工登录已失效", null);
                                    }
                                    if (staff.getStoreId() == null && !StpUtil.hasRole("ADMIN") && !StpUtil.hasRole("SUPER_ADMIN")) {
                                        throw new IllegalArgumentException("当前员工未分配门店，无法访问门店业务");
                                    }
                                } catch (NumberFormatException invalid) {
                                    throw NotLoginException.newInstance("STAFF", NotLoginException.KICK_OUT,
                                            "员工登录已失效", null);
                                }
                            }
                            // 客户端接口：校验CUST_前缀
                            else if (path.startsWith("/api/client/")) {
                                if (!loginId.startsWith("CUST_")) {
                                    throw NotLoginException.newInstance(
                                            "CUST", NotLoginException.KICK_OUT, "客户端接口需要客户登录", null);
                                }
                            }
                        }
                    });
        })).addPathPatterns("/**");
    }
}
