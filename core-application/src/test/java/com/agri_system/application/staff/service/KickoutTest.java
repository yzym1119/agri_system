package com.agri_system.application.staff.service;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.performance.service.StaffPerformanceApplicationService;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KickoutTest {

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private StaffAuthorityService authorityService;
    @Mock
    private StaffPerformanceApplicationService performanceApplicationService;

    @InjectMocks
    private StaffLifecycleService lifecycleService;

    @Test
    void disableShouldKickoutStaffSession() {
        Staff staff = new Staff();
        staff.setId(1L);
        staff.setStatus(1);
        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            lifecycleService.disable(1L);
            stp.verify(() -> StpUtil.kickout("STAFF_1"));
        }
        verify(staffRepository).save(any(Staff.class));
    }
}





