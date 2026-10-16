package com.agri_system.application.staff.service;

import com.agri_system.application.performance.service.StaffPerformanceApplicationService;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StoreRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KickoutTest {

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private StaffAuthorityService authorityService;
    @Mock
    private StaffPerformanceApplicationService performanceApplicationService;
    @Mock
    private StaffSessionOperator staffSessionOperator;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private SysRoleRepository roleRepository;
    @Mock
    private SysStaffRoleRepository staffRoleRepository;

    @InjectMocks
    private StaffLifecycleService lifecycleService;

    @Test
    void disableUsesTheSameGuardAsStaffUpdate() {
        lifecycleService.disable(1L);
        verify(authorityService).changeStaffStatus(1L, 2);
        verifyNoInteractions(staffRepository, staffSessionOperator);
    }
}
