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
 * 微信身份登录与禁止空档案自动注册回归
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
        wechatResult.setAppId("wx1111111111111111");
        org.springframework.test.util.ReflectionTestUtils.setField(authService,"wechatAppId","wx1111111111111111");
        wechatResult.setOpenId(openId);
        wechatResult.setSessionKey(sessionKey);
        
        lenient().when(wechatService.jscode2session(anyString())).thenReturn(wechatResult);
    }

    @AfterEach
    void tearDown() {
        SaTokenContextForThreadLocalStorage.clearBox();
    }
    
    @Test
    void newWechatIdentityMustNotCreateAnEmptyPhoneCustomerOrWallet() {
        ClientWechatLoginCommand command=new ClientWechatLoginCommand();command.setCode("test_code");
        assertThrows(IllegalStateException.class,()->authService.clientWechatLogin(command));
        verify(identityRepository).findWechatByAppAndOpenIdForUpdate("wx1111111111111111",openId);
        verify(customerRepository,never()).save(any());verify(walletRepository,never()).save(any());verify(identityRepository,never()).save(any());
    }

    private ClientWechatLoginCommand command(){var c=new ClientWechatLoginCommand();c.setCode("test_code");return c;}
    private CustomerIdentity identity(){var i=new CustomerIdentity();i.setId(1L);i.setCustomerId(1L);i.setAppId("wx1111111111111111");i.setIdentityType("WECHAT_OPENID");i.setIdentityValue(openId);i.setStatus(1);i.setAuthVersion(1L);return i;}
    private CustomerIdentity bindExisting(){
        var i=identity();var c=new Customer();c.setId(1L);c.setName("已核验客户");c.setPhone("13800000000");c.setStatus(1);c.setDeleted(0);c.setAuthVersion(1L);
        lenient().when(customerRepository.findAuthStateById(1L)).thenAnswer(call->Optional.of(new com.agri_system.domain.customer.model.CustomerAuthState(c.getId(),c.getStatus(),c.getDeleted(),c.getAuthVersion())));
        when(identityRepository.findWechatByAppAndOpenIdForUpdate("wx1111111111111111",openId)).thenReturn(Optional.of(i));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        lenient().when(identityRepository.findById(1L)).thenReturn(Optional.of(i));return i;
    }
    @Test void existingWechatLoginBindsIndependentTokenAndChecksCurrentIdentityEveryTime(){
        var i=bindExisting();var first=authService.clientWechatLogin(command());assertNotNull(first.getTokenValue());assertEquals(1L,authService.getCurrentCustomerId());
        var second=authService.clientWechatLogin(command());assertNotEquals(first.getTokenValue(),second.getTokenValue());
        cn.dev33.satoken.stp.StpUtil.setTokenValue(first.getTokenValue());assertEquals(1L,authService.getCurrentCustomerId(),"普通登录不撤销另一有效设备");
        cn.dev33.satoken.stp.StpUtil.setTokenValue(second.getTokenValue());
        assertEquals("138****0000",authService.getCurrentClientProfile().phoneMasked());
        i.setStatus(2);assertThrows(IllegalArgumentException.class,()->authService.getCurrentCustomerId());
        i.setStatus(1);i.setCustomerId(2L);assertThrows(IllegalArgumentException.class,()->authService.getCurrentCustomerId());
        i.setCustomerId(1L);org.springframework.test.util.ReflectionTestUtils.setField(authService,"wechatAppId","wx2222222222222222");assertThrows(IllegalArgumentException.class,()->authService.getCurrentCustomerId());
        verify(customerRepository,never()).save(any());verify(walletRepository,never()).save(any());
    }
    @Test void disabledWechatIdentityDoesNotUpdateSessionOrIssueCustomerLogin(){
        var i=identity();i.setStatus(2);when(identityRepository.findWechatByAppAndOpenIdForUpdate("wx1111111111111111",openId)).thenReturn(Optional.of(i));
        assertThrows(IllegalArgumentException.class,()->authService.clientWechatLogin(command()));verify(identityRepository,never()).save(any());verifyNoInteractions(customerRepository,walletRepository);
    }
    @Test void oldCustomerTokenWithoutVerifiedSourceCannotReadCustomerResources(){
        StpUtilLoginProbe.login();assertThrows(IllegalArgumentException.class,()->authService.getCurrentCustomerId());
        verifyNoInteractions(customerRepository,walletRepository);
    }
    @Test void identityVersionChangePermanentlyRevokesOldTokenEvenWhenAssociationReturns() {
        var identity=bindExisting();
        authService.clientWechatLogin(command());
        assertEquals(1L,authService.getCurrentCustomerId());
        identity.setStatus(2);identity.setAuthVersion(2L);
        assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);
        identity.setStatus(1);identity.setAuthVersion(3L);
        assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId,"恢复启用不能恢复旧会话");
        authService.clientWechatLogin(command());
        assertEquals(1L,authService.getCurrentCustomerId(),"重新平台登录使用当前版本");
        identity.setCustomerId(2L);identity.setAuthVersion(4L);
        assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);
        identity.setCustomerId(1L);identity.setAuthVersion(5L);
        assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId,"改回同人不恢复旧会话");
    }
    @Test void sourceMarkedWechatTokenWithoutEpochMustNotBorrowCurrentDatabaseVersion() {
        bindExisting();authService.clientWechatLogin(command());
        cn.dev33.satoken.stp.StpUtil.getTokenSession().set("clientAuthV1",Map.of("kind","WECHAT","identityId","1","appId","wx1111111111111111","customerAuthVersion","1"));
        assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);
    }
    @Test void malformedOrMissingPersistedVersionCannotIssueAWechatSession() {
        var identity=identity();when(identityRepository.findWechatByAppAndOpenIdForUpdate("wx1111111111111111",openId)).thenReturn(Optional.of(identity));identity.setAuthVersion(null);
        assertThrows(IllegalArgumentException.class,()->authService.clientWechatLogin(command()));
        identity.setAuthVersion(0L);assertThrows(IllegalArgumentException.class,()->authService.clientWechatLogin(command()));
        verify(identityRepository,never()).save(any());
    }
    @Test void legacyCustomerEpochCannotBorrowCurrentVersionAndRestoreNeverRevivesOldDevices(){
        var identity=bindExisting();var first=authService.clientWechatLogin(command());var c=customerRepository.findById(1L).orElseThrow();
        var original=cn.dev33.satoken.stp.StpUtil.getTokenSession().get("clientAuthV1");
        cn.dev33.satoken.stp.StpUtil.getTokenSession().set("clientAuthV1",Map.of("kind","WECHAT","identityId","1","appId","wx1111111111111111","authVersion","1"));
        assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);
        cn.dev33.satoken.stp.StpUtil.getTokenSession().set("clientAuthV1",original);
        c.setStatus(2);c.setAuthVersion(2L);assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);
        c.setStatus(1);c.setAuthVersion(3L);assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);
        var second=authService.clientWechatLogin(command());assertNotEquals(first.getTokenValue(),second.getTokenValue());assertEquals(1L,authService.getCurrentCustomerId());
        cn.dev33.satoken.stp.StpUtil.setTokenValue(first.getTokenValue());assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);assertEquals(1L,identity.getAuthVersion());
    }
    @Test void secondProfileReadCannotBorrowNewCustomerEpochAfterFirstCheck(){
        bindExisting();authService.clientWechatLogin(command());var original=customerRepository.findById(1L).orElseThrow();
        var changed=new Customer();changed.setId(1L);changed.setStatus(1);changed.setDeleted(0);changed.setAuthVersion(2L);changed.setName("已撤销后的资料");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(original),Optional.of(changed));
        assertThrows(IllegalArgumentException.class,authService::getCurrentClientProfile);
    }
    @Test void explicitDevLoginAlsoUsesPersistedCustomerGeneration(){
        var customer=new Customer();customer.setId(1L);customer.setStatus(1);customer.setDeleted(0);customer.setAuthVersion(1L);
        when(customerRepository.findByPhone("13800000000")).thenReturn(Optional.of(customer));when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.findAuthStateById(1L)).thenAnswer(call->Optional.of(new com.agri_system.domain.customer.model.CustomerAuthState(1L,customer.getStatus(),0,customer.getAuthVersion())));
        org.springframework.test.util.ReflectionTestUtils.setField(authService,"wechatAppId","");org.springframework.test.util.ReflectionTestUtils.setField(authService,"devLoginEnabled",true);
        authService.devClientLogin("13800000000");assertEquals(1L,authService.getCurrentCustomerId());customer.setStatus(2);customer.setAuthVersion(2L);assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);
        customer.setStatus(1);customer.setAuthVersion(3L);assertThrows(IllegalArgumentException.class,authService::getCurrentCustomerId);authService.devClientLogin("13800000000");assertEquals(1L,authService.getCurrentCustomerId());
    }
    private static class StpUtilLoginProbe {static void login(){cn.dev33.satoken.stp.StpUtil.login("CUST_1",new cn.dev33.satoken.stp.SaLoginModel().setToken(java.util.UUID.randomUUID().toString()));}}

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




