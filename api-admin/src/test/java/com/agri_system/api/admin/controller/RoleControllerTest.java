package com.agri_system.api.admin.controller;

import com.agri_system.api.admin.config.TestApplication;
import com.agri_system.application.staff.dto.command.RoleSaveCommand;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {TestApplication.class, RoleController.class})
class RoleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean StaffAuthorityService authorityService;
    @MockBean SysStaffRoleRepository staffRoleRepository;

    @Test
    void putUpdatesRoleById() throws Exception {
        RoleSaveCommand body = new RoleSaveCommand();
        body.setName("门店店长");
        body.setStatus(1);
        body.setMenuIds(List.of(100L));

        mockMvc.perform(put("/api/admin/roles/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        ArgumentCaptor<RoleSaveCommand> captor = ArgumentCaptor.forClass(RoleSaveCommand.class);
        verify(authorityService).updateRole(eq(5L), captor.capture());
        assertEquals("门店店长", captor.getValue().getName());
    }
}
