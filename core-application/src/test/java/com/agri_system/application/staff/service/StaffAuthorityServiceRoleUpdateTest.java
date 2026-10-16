package com.agri_system.application.staff.service;

import com.agri_system.application.common.service.CodeGenerationApplicationService;
import com.agri_system.application.staff.dto.command.RoleSaveCommand;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.model.SysRole;
import com.agri_system.domain.system.auth.repository.SysMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffAuthorityServiceRoleUpdateTest {

    @Mock StaffRepository staffRepository;
    @Mock SysRoleRepository roleRepository;
    @Mock SysMenuRepository menuRepository;
    @Mock SysRoleMenuRepository roleMenuRepository;
    @Mock SysStaffRoleRepository staffRoleRepository;
    @Mock CodeGenerationApplicationService codeGenerationService;

    @InjectMocks StaffAuthorityService service;

    private SysRole existingRole() {
        SysRole role = new SysRole();
        role.setId(5L);
        role.setName("店长");
        role.setCode("ROLE_0005");
        role.setStatus(1);
        return role;
    }

    @Test
    void updateRoleWithFrontendEditPayloadKeepsCodeAndLeavesMenusUntouched() {
        when(roleRepository.findById(5L)).thenReturn(Optional.of(existingRole()));
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("门店店长");
        command.setCode(null);
        command.setStatus(0);
        command.setMenuIds(List.of());

        service.updateRole(5L, command);

        ArgumentCaptor<SysRole> saved = ArgumentCaptor.forClass(SysRole.class);
        verify(roleRepository).save(saved.capture());
        assertEquals(5L, saved.getValue().getId());
        assertEquals("门店店长", saved.getValue().getName());
        assertEquals("ROLE_0005", saved.getValue().getCode());
        assertEquals(0, saved.getValue().getStatus());
        verifyNoInteractions(codeGenerationService);
        verifyNoInteractions(roleMenuRepository);
    }

    @Test
    void updateRoleIgnoresSubmittedCode() {
        when(roleRepository.findById(5L)).thenReturn(Optional.of(existingRole()));
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("门店店长");
        command.setCode("SUPER_ADMIN");

        service.updateRole(5L, command);

        ArgumentCaptor<SysRole> saved = ArgumentCaptor.forClass(SysRole.class);
        verify(roleRepository).save(saved.capture());
        assertEquals("ROLE_0005", saved.getValue().getCode());
    }

    @Test
    void updateRoleRejectsNonEmptyMenuIds() {
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("门店店长");
        command.setStatus(1);
        command.setMenuIds(List.of(100L, 200L));

        assertThrows(IllegalArgumentException.class, () -> service.updateRole(5L, command));
        verify(roleRepository, never()).save(any());
        verifyNoInteractions(roleMenuRepository);
    }

    @Test
    void updateRoleLeavesMenusUntouchedWhenMenuIdsAbsent() {
        when(roleRepository.findById(5L)).thenReturn(Optional.of(existingRole()));
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("门店店长");

        service.updateRole(5L, command);

        verifyNoInteractions(roleMenuRepository);
    }

    @Test
    void updateRoleRejectsMissingRole() {
        when(roleRepository.findById(9L)).thenReturn(Optional.empty());
        RoleSaveCommand command = new RoleSaveCommand();
        command.setName("x");

        assertThrows(IllegalArgumentException.class, () -> service.updateRole(9L, command));
        verify(roleRepository, never()).save(any());
    }
}
