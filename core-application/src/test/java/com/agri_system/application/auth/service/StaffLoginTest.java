package com.agri_system.application.auth.service;

import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.dto.StaffLoginCommand;
import com.agri_system.application.auth.util.BCryptUtils;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 员工登录测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class StaffLoginTest {
    
    @Mock
    private StaffRepository staffRepository;
    
    @Mock
    private com.agri_system.domain.customer.repository.CustomerRepository customerRepository;
    
    @Mock
    private com.agri_system.domain.auth.repository.CustomerIdentityRepository identityRepository;
    
    @Mock
    private com.agri_system.domain.wallet.repository.CustomerWalletRepository walletRepository;
    
    @Mock
    private WechatService wechatService;
    
    @InjectMocks
    private AuthService authService;
    
    private Staff staff;
    private String encodedPassword;
    
    @BeforeEach
    void setUp() {
        // 创建测试员工
        staff = new Staff();
        staff.setId(1L);
        staff.setCode("STAFF001");
        staff.setName("测试员工");
        staff.setPhone("13800138000");
        staff.setRole("ADMIN");
        staff.setStatus(1);
        staff.setStoreId(1L);
        
        // 加密密码
        encodedPassword = BCryptUtils.encode("123456");
        staff.setPassword(encodedPassword);
    }
    
    @Test
    void testNormalLogin() {
        // 准备
        StaffLoginCommand command = new StaffLoginCommand();
        command.setAccount("STAFF001");
        command.setPassword("123456");
        
        when(staffRepository.findByCodeOrPhone("STAFF001")).thenReturn(Optional.of(staff));
        
        // 执行
        LoginResultVO result = authService.staffLogin(command);
        
        // 验证
        assertNotNull(result);
        assertNotNull(result.getTokenValue());
        assertNotNull(result.getUserInfo());
        assertEquals(1L, result.getUserInfo().getId());
        assertEquals("测试员工", result.getUserInfo().getName());
        assertEquals("ADMIN", result.getUserInfo().getRole());
        
        verify(staffRepository, times(1)).findByCodeOrPhone("STAFF001");
    }
    
    @Test
    void testPasswordError() {
        // 准备
        StaffLoginCommand command = new StaffLoginCommand();
        command.setAccount("STAFF001");
        command.setPassword("wrong_password");
        
        when(staffRepository.findByCodeOrPhone("STAFF001")).thenReturn(Optional.of(staff));
        
        // 执行和验证
        assertThrows(IllegalArgumentException.class, () -> {
            authService.staffLogin(command);
        }, "密码错误");
        
        verify(staffRepository, times(1)).findByCodeOrPhone("STAFF001");
    }
    
    @Test
    void testAccountDisabled() {
        // 准备
        staff.setStatus(2); // 离职
        
        StaffLoginCommand command = new StaffLoginCommand();
        command.setAccount("STAFF001");
        command.setPassword("123456");
        
        when(staffRepository.findByCodeOrPhone("STAFF001")).thenReturn(Optional.of(staff));
        
        // 执行和验证
        assertThrows(IllegalArgumentException.class, () -> {
            authService.staffLogin(command);
        }, "账号已停用");
        
        verify(staffRepository, times(1)).findByCodeOrPhone("STAFF001");
    }
    
    @Test
    void testAccountNotFound() {
        // 准备
        StaffLoginCommand command = new StaffLoginCommand();
        command.setAccount("NOTEXIST");
        command.setPassword("123456");
        
        when(staffRepository.findByCodeOrPhone("NOTEXIST")).thenReturn(Optional.empty());
        
        // 执行和验证
        assertThrows(IllegalArgumentException.class, () -> {
            authService.staffLogin(command);
        }, "账号不存在");
        
        verify(staffRepository, times(1)).findByCodeOrPhone("NOTEXIST");
    }
}

