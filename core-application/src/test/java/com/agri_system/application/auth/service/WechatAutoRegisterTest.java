package com.agri_system.application.auth.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContextForThreadLocal;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStorage;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.context.model.SaResponse;
import cn.dev33.satoken.context.model.SaStorage;
import com.agri_system.application.auth.dto.ClientWechatLoginCommand;
import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.wallet.model.CustomerWallet;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 微信自动注册测试
 * 
 * @author AI Generated
 */
@ExtendWith(MockitoExtension.class)
class WechatAutoRegisterTest {
    
    @Mock
    private com.agri_system.domain.store.repository.StaffRepository staffRepository;
    
    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private CustomerIdentityRepository identityRepository;
    
    @Mock
    private CustomerWalletRepository walletRepository;
    
    @Mock
    private WechatService wechatService;

    @Mock
    private com.agri_system.domain.system.auth.repository.SysRoleRepository roleRepository;

    @Mock
    private StaffAuthorityService authorityService;
    
    @InjectMocks
    private AuthService authService;
    
    private String openId = "test_openid_123456";
    private String sessionKey = "test_session_key";
    
    @BeforeEach
    void setUp() {
        SaManager.setSaTokenContext(new SaTokenContextForThreadLocal());
        SaTokenContextForThreadLocalStorage.setBox(new MockSaRequest(), new MockSaResponse(), new MockSaStorage());

        // Mock微信服务返回
        WechatService.WechatLoginResult wechatResult = new WechatService.WechatLoginResult();
        wechatResult.setOpenId(openId);
        wechatResult.setSessionKey(sessionKey);
        
        when(wechatService.jscode2session(anyString())).thenReturn(wechatResult);
    }

    @AfterEach
    void tearDown() {
        SaTokenContextForThreadLocalStorage.clearBox();
    }
    
    @Test
    void testWechatAutoRegister() {
        // 准备：新用户，不存在身份
        ClientWechatLoginCommand command = new ClientWechatLoginCommand();
        command.setCode("test_code");
        
        when(identityRepository.findByTypeAndValue("WECHAT_OPENID", openId))
                .thenReturn(Optional.empty());
        
        // Mock保存操作
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(1L);
            return customer;
        });
        
        when(identityRepository.save(any(CustomerIdentity.class))).thenAnswer(invocation -> {
            CustomerIdentity identity = invocation.getArgument(0);
            identity.setId(1L);
            return identity;
        });
        
        when(walletRepository.save(any(CustomerWallet.class))).thenAnswer(invocation -> {
            CustomerWallet wallet = invocation.getArgument(0);
            wallet.setId(1L);
            return wallet;
        });
        
        // 执行
        LoginResultVO result = authService.clientWechatLogin(command);
        
        // 验证
        assertNotNull(result);
        assertNotNull(result.getTokenValue());
        assertNotNull(result.getUserInfo());
        assertEquals(1L, result.getUserInfo().getId());
        
        // 验证创建了客户
        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository, times(1)).save(customerCaptor.capture());
        Customer savedCustomer = customerCaptor.getValue();
        assertNotNull(savedCustomer.getCode());
        assertTrue(savedCustomer.getCode().startsWith("CUST_"));
        assertEquals(1, savedCustomer.getType());
        assertEquals(1, savedCustomer.getStatus());
        
        // 验证创建了身份
        ArgumentCaptor<CustomerIdentity> identityCaptor = ArgumentCaptor.forClass(CustomerIdentity.class);
        verify(identityRepository, times(1)).save(identityCaptor.capture());
        CustomerIdentity savedIdentity = identityCaptor.getValue();
        assertEquals("WECHAT_OPENID", savedIdentity.getIdentityType());
        assertEquals(openId, savedIdentity.getIdentityValue());
        assertEquals(sessionKey, savedIdentity.getSessionKey());
        
        // 验证创建了三钱包
        ArgumentCaptor<CustomerWallet> walletCaptor = ArgumentCaptor.forClass(CustomerWallet.class);
        verify(walletRepository, times(3)).save(walletCaptor.capture());
        
        walletCaptor.getAllValues().forEach(wallet -> {
            assertEquals(1L, wallet.getCustomerId());
            assertNotNull(wallet.getWalletType());
            assertTrue(wallet.getWalletType() >= 1 && wallet.getWalletType() <= 3);
            assertEquals(BigDecimal.ZERO, wallet.getBalance());
            assertEquals(BigDecimal.ZERO, wallet.getFrozenBalance());
        });
    }
    
    @Test
    void testWechatLoginExistingUser() {
        // 准备：已存在用户
        ClientWechatLoginCommand command = new ClientWechatLoginCommand();
        command.setCode("test_code");
        
        CustomerIdentity existingIdentity = new CustomerIdentity();
        existingIdentity.setId(1L);
        existingIdentity.setCustomerId(1L);
        existingIdentity.setIdentityType("WECHAT_OPENID");
        existingIdentity.setIdentityValue(openId);
        existingIdentity.setSessionKey("old_session_key");
        
        Customer existingCustomer = new Customer();
        existingCustomer.setId(1L);
        existingCustomer.setCode("CUST_EXISTING");
        existingCustomer.setName("已存在用户");
        existingCustomer.setType(1);
        existingCustomer.setStatus(1);
        
        when(identityRepository.findByTypeAndValue("WECHAT_OPENID", openId))
                .thenReturn(Optional.of(existingIdentity));
        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(existingCustomer));
        when(identityRepository.save(any(CustomerIdentity.class))).thenReturn(existingIdentity);
        
        // 执行
        LoginResultVO result = authService.clientWechatLogin(command);
        
        // 验证
        assertNotNull(result);
        assertNotNull(result.getTokenValue());
        assertEquals(1L, result.getUserInfo().getId());
        
        // 验证更新了SessionKey
        ArgumentCaptor<CustomerIdentity> identityCaptor = ArgumentCaptor.forClass(CustomerIdentity.class);
        verify(identityRepository, times(1)).save(identityCaptor.capture());
        assertEquals(sessionKey, identityCaptor.getValue().getSessionKey());
        
        // 验证没有创建新客户
        verify(customerRepository, never()).save(any(Customer.class));
        
        // 验证没有创建新钱包
        verify(walletRepository, never()).save(any(CustomerWallet.class));
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




