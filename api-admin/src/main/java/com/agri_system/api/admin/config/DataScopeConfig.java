package com.agri_system.api.admin.config;

import com.agri_system.application.auth.util.CurrentUser;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 数据范围配置 — 门店级数据隔离。
 *
 * <p>启用 MyBatis-Flex 的 {@link TenantManager}，对标记 {@code @Column(tenantId = true)} 的
 * {@code store_id} 字段自动追加 {@code WHERE store_id = ?} 条件，
 * 使非管理员角色只能访问自己门店的数据。</p>
 *
 * <p>管理员角色（SUPER_ADMIN / ADMIN）通过 {@link CurrentUser#isAdmin()} 判定后跳过租户条件，
 * 可见全部门店的数据。</p>
 */
@Slf4j
@Configuration
public class DataScopeConfig implements WebMvcConfigurer {

    @PostConstruct
    public void initTenant() {
        TenantManager.setTenantFactory(new StoreTenantFactory());
        log.info("[DataScope] 门店级数据隔离已启用");
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new RequestCacheCleaner()).order(Integer.MIN_VALUE).addPathPatterns("/**");
    }

    /** 每个请求结束后清理 CurrentUser 的 ThreadLocal 缓存，避免线程池复用时数据串位 */
    private static final class RequestCacheCleaner implements HandlerInterceptor {
        @Override
        public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                 @NonNull Object handler) {
            CurrentUser.clearRequestCache();
            return true;
        }
        @Override
        public void afterCompletion(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull Object handler,
                                    Exception ex) {
            CurrentUser.clearRequestCache();
        }
    }

    /**
     * 门店租户工厂：每个 SQL 执行时决定当前用户的门店 ID。
     * 管理员返回 null → 不追加 WHERE 条件；普通员工返回 [storeId] → 自动追加过滤。
     */
    private static final class StoreTenantFactory implements TenantFactory {
        @Override
        public Object[] getTenantIds() {
            if (CurrentUser.isAdmin()) return null;
            Long sid = CurrentUser.storeId();
            return new Object[]{sid};
        }
    }
}
