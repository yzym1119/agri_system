package com.agri_system.application.auth.util;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 当前登录用户上下文工具类。
 *
 * <p>所有方法均为静态调用，便于在 Service / Interceptor / TenantFactory 中快速获取当前登录员工的信息。
 * 为避免同一请求内重复查询 DB，使用 ThreadLocal 做请求级缓存；同时在登录时把 storeId / roleCodes
 * 写入 Sa-Token Session，跨请求复用直到重新登录。</p>
 *
 * <p>仅供员工（STAFF_）登录态使用，客户（CUST_）登录态下所有方法返回 null / false。</p>
 */
@Component
public final class CurrentUser {

    private static StaffRepository staffRepository;
    private static SysRoleRepository roleRepository;

    @Autowired
    public void setStaffRepository(StaffRepository staffRepository) {
        CurrentUser.staffRepository = staffRepository;
    }

    @Autowired
    public void setRoleRepository(SysRoleRepository roleRepository) {
        CurrentUser.roleRepository = roleRepository;
    }

    /** 当前登录员工ID；未登录或非员工登录返回 null */
    public static Long staffId() {
        RequestCache cache = RequestCache.get();
        if (cache.staffIdResolved) return cache.staffId;
        cache.staffIdResolved = true;

        if (!StpUtil.isLogin()) return null;
        Object loginIdObj = StpUtil.getLoginId();
        if (loginIdObj == null) return null;
        String loginId = loginIdObj.toString();
        if (!loginId.startsWith("STAFF_")) return null;
        try {
            cache.staffId = Long.parseLong(loginId.substring(6));
        } catch (NumberFormatException ignored) { }
        return cache.staffId;
    }

    /** 当前登录员工所属门店ID */
    public static Long storeId() {
        RequestCache cache = RequestCache.get();
        if (cache.storeIdResolved) return cache.storeId;
        cache.storeIdResolved = true;

        Long id = staffId();
        if (id == null) return null;

        // 优先 Sa-Token Session 缓存（登录时写入）
        try {
            SaSession session = StpUtil.getSession(false);
            if (session != null) {
                Object sid = session.get("storeId");
                if (sid instanceof Number) {
                    cache.storeId = ((Number) sid).longValue();
                    return cache.storeId;
                }
            }
        } catch (Exception ignored) { }

        // 回退：查 staff 表并回写 Session
        if (staffRepository != null) {
            Staff staff = staffRepository.findById(id).orElse(null);
            if (staff != null) {
                cache.storeId = staff.getStoreId();
                try {
                    if (cache.storeId != null && StpUtil.isLogin()) {
                        StpUtil.getSession(true).set("storeId", cache.storeId);
                    }
                } catch (Exception ignored) { }
            }
        }
        return cache.storeId;
    }

    /** 当前登录员工是否具备管理员角色（SUPER_ADMIN 或 ADMIN） */
    public static boolean isAdmin() {
        RequestCache cache = RequestCache.get();
        if (cache.isAdminResolved) return cache.isAdmin;
        cache.isAdminResolved = true;

        Long id = staffId();
        if (id == null) {
            // 未登录默认不做租户过滤（避免污染匿名请求）
            cache.isAdmin = true;
            return true;
        }

        // 优先 Sa-Token Session 缓存
        try {
            SaSession session = StpUtil.getSession(false);
            if (session != null) {
                Object codes = session.get("roleCodes");
                if (codes instanceof List<?> list) {
                    cache.isAdmin = list.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .anyMatch(c -> "SUPER_ADMIN".equals(c) || "ADMIN".equals(c));
                    return cache.isAdmin;
                }
            }
        } catch (Exception ignored) { }

        // 回退：查角色表并回写 Session
        List<String> roleCodes = roleRepository != null
                ? roleRepository.findRoleCodesByStaffId(id)
                : Collections.emptyList();
        cache.isAdmin = roleCodes.stream()
                .anyMatch(c -> "SUPER_ADMIN".equals(c) || "ADMIN".equals(c));
        try {
            if (StpUtil.isLogin()) {
                StpUtil.getSession(true).set("roleCodes", roleCodes);
            }
        } catch (Exception ignored) { }
        return cache.isAdmin;
    }

    /** 清理请求级缓存（必须在请求结束时调用，例如 Filter / Interceptor 的 afterCompletion） */
    public static void clearRequestCache() {
        RequestCache.CACHE.remove();
    }

    private static final class RequestCache {
        static final ThreadLocal<RequestCache> CACHE = ThreadLocal.withInitial(RequestCache::new);

        Long staffId;
        boolean staffIdResolved;
        Long storeId;
        boolean storeIdResolved;
        boolean isAdmin;
        boolean isAdminResolved;

        static RequestCache get() {
            return CACHE.get();
        }
    }
}
