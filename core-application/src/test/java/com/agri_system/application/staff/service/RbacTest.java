package com.agri_system.application.staff.service;

import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacTest {

    @Mock
    private StaffAuthorityService authorityService;

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private StaffStpInterface stpInterface;

    @Test
    void shouldReturnPermissionsFromAuthorityService() {
        Staff staff=new Staff();staff.setId(1L);staff.setStatus(1);staff.setDeleted(0);
        when(staffRepository.findIdentityById(1L)).thenReturn(Optional.of(staff));
        when(authorityService.loadPermissions(1L)).thenReturn(List.of("order:list", "order:create"));
        List<String> permissions = stpInterface.getPermissionList("STAFF_1", null);
        assertEquals(List.of("order:list", "order:create"), permissions);
    }
}





