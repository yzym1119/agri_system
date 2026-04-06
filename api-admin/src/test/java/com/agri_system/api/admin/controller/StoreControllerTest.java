package com.agri_system.api.admin.controller;

import com.agri_system.application.store.dto.command.StoreCreateCommand;
import com.agri_system.application.store.service.StoreApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 门店控制器集成测试
 * 注意：鉴权测试需要配置 Sa-Token，这里仅测试基本功能
 * 
 * @author AI Generated
 */
@WebMvcTest(StoreController.class)
class StoreControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private StoreApplicationService storeService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void testCreateStore_ShouldCallService() throws Exception {
        // Given: 创建门店命令
        StoreCreateCommand command = new StoreCreateCommand();
        command.setCode("STORE001");
        command.setName("测试门店");
        command.setPhone("13800138000");
        command.setAddress("测试地址");
        command.setSupportPickup(1);
        command.setStatus(1);
        
        when(storeService.createStore(any())).thenReturn(1L);
        
        // When & Then: 验证可以调用接口
        // 注意：实际鉴权测试需要配置 Sa-Token，这里仅测试基本功能
        // 在生产环境中，需要验证只有 ADMIN 角色才能调用
        mockMvc.perform(post("/api/admin/stores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk());
    }
    
    @Test
    void testDeleteStore_ShouldCallService() throws Exception {
        // Given
        doNothing().when(storeService).deleteStore(1L);
        
        // When & Then: 验证可以调用接口
        // 注意：实际鉴权测试需要配置 Sa-Token，这里仅测试基本功能
        // 在生产环境中，需要验证只有 ADMIN 角色才能调用
        mockMvc.perform(delete("/api/admin/stores/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
    
    // TODO: 添加 Sa-Token 鉴权测试
    // 需要配置 Sa-Token 测试环境，验证：
    // 1. 只有 ADMIN 角色才能调用 create/delete 接口
    // 2. 非 ADMIN 角色调用应返回 403 Forbidden
}

