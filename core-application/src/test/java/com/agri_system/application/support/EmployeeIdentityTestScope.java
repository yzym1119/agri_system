package com.agri_system.application.support;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

/** 使用真实CurrentUser和SaToken，测试结束恢复全局注入，避免static mock与测试串扰。 */
public final class EmployeeIdentityTestScope implements AutoCloseable {
    private final SaTokenContext oldContext=SaManager.getSaTokenContext();
    private final Object oldStaff=get("staffRepository"), oldRole=get("roleRepository");
    public EmployeeIdentityTestScope(Long id,Long storeId,boolean admin) {
        SaTokenTestContext.setUp();CurrentUser.clearRequestCache();
        var staffRepository=mock(StaffRepository.class);var roleRepository=mock(SysRoleRepository.class);
        var current=new CurrentUser();current.setStaffRepository(staffRepository);current.setRoleRepository(roleRepository);
        if(id!=null) {
            var staff=new Staff();staff.setId(id);staff.setStoreId(storeId);staff.setStatus(1);staff.setDeleted(0);
            when(staffRepository.findIdentityById(id)).thenReturn(Optional.of(staff));
            when(roleRepository.findRoleCodesByStaffId(id)).thenReturn(admin?List.of("ADMIN"):List.of("TEST_STAFF"));
            StpUtil.login("STAFF_"+id);
        }
    }
    public void close() {
        try {StpUtil.logout();} finally {
            CurrentUser.clearRequestCache();SaTokenTestContext.tearDown();SaManager.setSaTokenContext(oldContext);
            set("staffRepository",oldStaff);set("roleRepository",oldRole);
        }
    }
    private static Object get(String name) {
        try {Field field=CurrentUser.class.getDeclaredField(name);field.setAccessible(true);return field.get(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void set(String name,Object value) {
        try {Field field=CurrentUser.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}
