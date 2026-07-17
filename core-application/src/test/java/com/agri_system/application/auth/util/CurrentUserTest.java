package com.agri_system.application.auth.util;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * CurrentUser 工具类测试 — 验证数据隔离基础设施
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private SysRoleRepository roleRepository;

    @BeforeEach
    void setUp() {
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
        SaTokenContextForThreadLocalStorage.setBox(new MockSaRequest(), new MockSaResponse(), new MockSaStorage());

        CurrentUser instance = new CurrentUser();
        instance.setStaffRepository(staffRepository);
        instance.setRoleRepository(roleRepository);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clearRequestCache();
        try { StpUtil.logout(); } catch (Exception ignored) {}
        SaTokenContextForThreadLocalStorage.clearBox();
    }

    @Test
    void staffId_shouldReturnNullWhenNotLoggedIn() {
        assertNull(CurrentUser.staffId());
    }

    @Test
    void staffId_shouldReturnIdWhenStaffLoggedIn() {
        StpUtil.login("STAFF_42");
        assertEquals(42L, CurrentUser.staffId());
    }

    @Test
    void staffId_shouldReturnNullForCustomerLogin() {
        StpUtil.login("CUST_100");
        assertNull(CurrentUser.staffId());
    }

    @Test
    void storeId_shouldReturnNullWhenNotLoggedIn() {
        assertNull(CurrentUser.storeId());
    }

    @Test
    void storeId_shouldReturnStoreIdFromStaff() {
        StpUtil.login("STAFF_1");
        Staff staff = new Staff();
        staff.setId(1L);
        staff.setStoreId(100L);

        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        assertEquals(100L, CurrentUser.storeId());
    }

    @Test
    void isAdmin_shouldReturnTrueWhenNotLoggedIn() {
        assertTrue(CurrentUser.isAdmin());
    }

    @Test
    void isAdmin_shouldReturnTrueForSuperAdmin() {
        StpUtil.login("STAFF_1");
        when(roleRepository.findRoleCodesByStaffId(1L))
                .thenReturn(List.of("SUPER_ADMIN"));

        assertTrue(CurrentUser.isAdmin());
    }

    @Test
    void isAdmin_shouldReturnTrueForAdmin() {
        StpUtil.login("STAFF_2");
        when(roleRepository.findRoleCodesByStaffId(2L))
                .thenReturn(List.of("ADMIN"));

        assertTrue(CurrentUser.isAdmin());
    }

    @Test
    void isAdmin_shouldReturnFalseForNormalStaff() {
        StpUtil.login("STAFF_3");
        when(roleRepository.findRoleCodesByStaffId(3L))
                .thenReturn(List.of("SALES"));

        assertFalse(CurrentUser.isAdmin());
    }

    @Test
    void isAdmin_shouldReturnFalseForEmptyRoles() {
        StpUtil.login("STAFF_4");
        when(roleRepository.findRoleCodesByStaffId(4L))
                .thenReturn(Collections.emptyList());

        assertFalse(CurrentUser.isAdmin());
    }

    @Test
    void clearRequestCache_shouldAllowReQuery() {
        StpUtil.login("STAFF_1");
        Staff staff = new Staff();
        staff.setId(1L);
        staff.setStoreId(100L);
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(roleRepository.findRoleCodesByStaffId(1L)).thenReturn(List.of("SUPER_ADMIN"));

        // 触发缓存
        CurrentUser.staffId();
        CurrentUser.storeId();
        CurrentUser.isAdmin();

        // 清除缓存
        CurrentUser.clearRequestCache();

        // 再次调用
        assertEquals(1L, CurrentUser.staffId());
    }

    // ========== Mock 类 ==========

    private static class MockSaStorage implements SaStorage {
        private final Map<String, Object> storage = new HashMap<>();
        @Override public Object getSource() { return storage; }
        @Override public Object get(String key) { return storage.get(key); }
        @Override public SaStorage set(String key, Object value) { storage.put(key, value); return this; }
        @Override public SaStorage delete(String key) { storage.remove(key); return this; }
    }

    private static class MockSaRequest implements SaRequest {
        @Override public Object getSource() { return this; }
        @Override public String getParam(String name) { return null; }
        @Override public List<String> getParamNames() { return List.of(); }
        @Override public Map<String, String> getParamMap() { return Map.of(); }
        @Override public String getHeader(String name) { return null; }
        @Override public String getCookieValue(String name) { return null; }
        @Override public String getRequestPath() { return "/test"; }
        @Override public String getUrl() { return "/test"; }
        @Override public String getMethod() { return "GET"; }
        @Override public Object forward(String path) { return null; }
    }

    private static class MockSaResponse implements SaResponse {
        @Override public Object getSource() { return this; }
        @Override public SaResponse setStatus(int sc) { return this; }
        @Override public SaResponse setHeader(String name, String value) { return this; }
        @Override public SaResponse addHeader(String name, String value) { return this; }
        @Override public Object redirect(String url) { return null; }
    }
}
