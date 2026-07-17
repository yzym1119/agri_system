package com.agri_system.application.auth.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.dto.StaffLoginCommand;
import com.agri_system.application.auth.util.BCryptUtils;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private com.agri_system.domain.system.auth.repository.SysRoleRepository roleRepository;

    @Mock
    private StaffAuthorityService authorityService;

    @Mock
    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private AuthService authService;
    
    private Staff staff;
    private String encodedPassword;
    
    @BeforeEach
    void setUp() {
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
        SaTokenContextForThreadLocalStorage.setBox(new MockSaRequest(), new MockSaResponse(), new MockSaStorage());

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

        lenient().when(roleRepository.findRoleCodesByStaffId(1L)).thenReturn(List.of("ADMIN"));
        lenient().when(authorityService.loadPermissions(1L)).thenReturn(List.of("staff:view"));

        // AuthService 登录限流依赖 Redis：mock 成"未被限流"
        valueOps = mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.get(anyString())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        SaTokenContextForThreadLocalStorage.clearBox();
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

    private static class MockSaStorage implements SaStorage {
        private final Map<String, Object> storage = new HashMap<>();

        @Override
        public Object getSource() {
            return storage;
        }

        @Override
        public Object get(String key) {
            return storage.get(key);
        }

        @Override
        public SaStorage set(String key, Object value) {
            storage.put(key, value);
            return this;
        }

        @Override
        public SaStorage delete(String key) {
            storage.remove(key);
            return this;
        }
    }

    private static class MockSaRequest implements SaRequest {
        @Override
        public Object getSource() {
            return this;
        }

        @Override
        public String getParam(String name) {
            return null;
        }

        @Override
        public List<String> getParamNames() {
            return List.of();
        }

        @Override
        public Map<String, String> getParamMap() {
            return Map.of();
        }

        @Override
        public String getHeader(String name) {
            return null;
        }

        @Override
        public String getCookieValue(String name) {
            return null;
        }

        @Override
        public String getRequestPath() {
            return "/";
        }

        @Override
        public String getUrl() {
            return "http://localhost/test";
        }

        @Override
        public String getMethod() {
            return "POST";
        }

        @Override
        public Object forward(String path) {
            return null;
        }
    }

    private static class MockSaResponse implements SaResponse {
        @Override
        public Object getSource() {
            return this;
        }

        @Override
        public SaResponse setStatus(int sc) {
            return this;
        }

        @Override
        public SaResponse setHeader(String name, String value) {
            return this;
        }

        @Override
        public SaResponse addHeader(String name, String value) {
            return this;
        }

        @Override
        public Object redirect(String url) {
            return null;
        }
    }
}
