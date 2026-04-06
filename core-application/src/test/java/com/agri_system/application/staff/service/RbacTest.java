package com.agri_system.application.staff.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RbacTest {

    @Mock
    private StaffAuthorityService authorityService;

    @InjectMocks
    private StaffStpInterface stpInterface;

    @Test
    void shouldReturnPermissionsFromAuthorityService() {
        when(authorityService.loadPermissions(1L)).thenReturn(List.of("order:list", "order:create"));
        List<String> permissions = stpInterface.getPermissionList("STAFF_1", null);
        assertEquals(2, permissions.size());
    }
}





