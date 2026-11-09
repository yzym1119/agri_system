package com.agri_system.api.client.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.after_sales.dto.command.ClientApplyCommand;
import com.agri_system.application.after_sales.service.AfterSalesApplicationService;
import com.agri_system.application.auth.service.AuthService;
import com.agri_system.application.auth.service.WechatService;
import com.agri_system.application.order.dto.command.*;
import com.agri_system.application.order.dto.vo.OrderPayPreviewVO;
import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.wallet.repository.CustomerWalletRepository;
import com.agri_system.infrastructure.auth.config.SaTokenConfigure;
import com.agri_system.infrastructure.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 真实 Spring MVC、Bean Validation、Sa-Token 拦截器及 AuthService 身份解析。
 * 业务应用服务为 mock：只证明 HTTP 边界与身份传参，不证明归属校验、支付幂等、并发或钱货落库。
 * 登录探针仅存在于本测试配置，不接真实微信、Redis、数据库或支付平台。
 */
@SpringBootTest(classes = ClientHttpBoundaryTest.Config.class, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                + "com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration",
        "spring.flyway.enabled=false", "sa-token.is-print=false", "payment.callback.secret=测试回调密钥",
        "auth.dev-login.enabled=true", "wechat.app-id="})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClientHttpBoundaryTest {
    private static final cn.dev33.satoken.config.SaTokenConfig OLD_CONFIG = SaManager.getConfig();
    private static final cn.dev33.satoken.context.SaTokenContext OLD_CONTEXT = SaManager.getSaTokenContext();
    private static final cn.dev33.satoken.stp.StpInterface OLD_STP = SaManager.getStpInterface();
    private static final SaTokenDao OLD_DAO = SaManager.getSaTokenDao();
    private static final String CREATE = "{\"storeId\":1,\"customerId\":202,\"orderType\":2,"
            + "\"items\":[{\"productId\":11,\"quantity\":1,\"unitPrice\":0.01}]}";
    private static final String PAY = "{\"orderId\":999,\"useBalance\":false,\"useSpecialFund\":false}";
    private static final String CONFIRM = "{\"intentNo\":\"PI-test\",\"externalTransactionNo\":\"wx-test\"}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OrderPaymentPublicController callbacks;
    @Autowired AuthService authentication;
    @Autowired com.agri_system.application.auth.service.ClientOnboardingService onboarding;
    @MockBean com.agri_system.application.auth.service.WechatPhoneAuthorizationService phoneAuthorization;
    @MockBean OrderApplicationService orders;
    @MockBean AfterSalesApplicationService afterSales;
    @MockBean CustomerRepository customers;
    @MockBean StaffRepository staff;
    @MockBean CustomerIdentityRepository identities;
    @MockBean CustomerWalletRepository wallets;
    @MockBean WechatService wechat;
    @MockBean SysRoleRepository roles;
    @MockBean StaffAuthorityService authority;
    @MockBean StringRedisTemplate redis;
    @MockBean com.agri_system.application.performance.service.OneTimeQrService oldQr;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SaTokenConfigure.class, AuthService.class, GlobalExceptionHandler.class,
            OrderClientController.class, AfterSalesClientController.class,
            OrderPaymentPublicController.class, AuthClientController.class, ClientOnboardingController.class, com.agri_system.application.auth.service.ClientOnboardingService.class, QrClientController.class, LoginProbe.class})
    static class Config {
        @Bean SaTokenDao saTokenDao() { return new SaTokenDaoDefaultImpl(); }
    }

    @RestController
    static class LoginProbe {
        @GetMapping("/auth/__client_test__/login")
        Map<String, String> login(@RequestParam String id) {
            StpUtil.login(id,new cn.dev33.satoken.stp.SaLoginModel().setToken(java.util.UUID.randomUUID().toString()));
            // 仅此隔离测试配置的显式开发身份；不伪装真实微信登录。
            StpUtil.getTokenSession().set("clientAuthV1",Map.of("kind","DEV"));
            return Map.of("token", StpUtil.getTokenValue());
        }
    }

    @BeforeEach void customerAndCallback() {
        ReflectionTestUtils.setField(authentication,"wechatAppId","");
        ReflectionTestUtils.setField(onboarding,"appId","");
        Customer active = new Customer(); active.setId(101L); active.setStatus(1);
        Customer disabled = new Customer(); disabled.setId(303L); disabled.setStatus(2);
        when(customers.findById(101L)).thenReturn(Optional.of(active));
        when(customers.findById(303L)).thenReturn(Optional.of(disabled));
        ReflectionTestUtils.setField(callbacks, "callbackSecret", "测试回调密钥");
    }

    @Test void actualWechatLoginAndCurrentProfileRejectRevokedOrReconfiguredIdentity() throws Exception {
        String app="wx1111111111111111";ReflectionTestUtils.setField(authentication,"wechatAppId",app);
        var platform=new WechatService.WechatLoginResult();platform.setAppId(app);platform.setOpenId("TEST_OPEN_ID");platform.setSessionKey("TEST_SESSION_KEY");when(wechat.jscode2session("test-code")).thenReturn(platform);
        var identity=new com.agri_system.domain.auth.model.CustomerIdentity();identity.setId(7L);identity.setAppId(app);identity.setCustomerId(101L);identity.setIdentityType("WECHAT_OPENID");identity.setIdentityValue("TEST_OPEN_ID");identity.setStatus(1);identity.setAuthVersion(1L);
        when(identities.findWechatByAppAndOpenIdForUpdate(app,"TEST_OPEN_ID")).thenReturn(Optional.of(identity));when(identities.findById(7L)).thenReturn(Optional.of(identity));
        var response=mvc.perform(post("/auth/client/login/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"test-code\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String token=json.readTree(response.getResponse().getContentAsString()).path("tokenValue").asText();assertFalse(token.isBlank());
        mvc.perform(get("/auth/client/info").header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.customerId").value("101")).andExpect(jsonPath("$.bindingState").value("LINKED")).andExpect(jsonPath("$.sessionKey").doesNotExist()).andExpect(jsonPath("$.openid").doesNotExist());
        identity.setStatus(2);identity.setAuthVersion(2L);businessFailure(request(get("/api/client/orders/77"),token,null),10001);verifyNoInteractions(orders);
        identity.setStatus(1);identity.setAuthVersion(3L);businessFailure(request(get("/auth/client/info"),token,null),10001);
        var relogin=mvc.perform(post("/auth/client/login/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"test-code\"}" )).andExpect(status().isOk()).andReturn();
        String fresh=json.readTree(relogin.getResponse().getContentAsString()).path("tokenValue").asText();assertNotEquals(token,fresh);
        mvc.perform(get("/auth/client/info").header("Authorization",fresh)).andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value("101"));
        businessFailure(request(get("/api/client/orders/77"),token,null),10001);verifyNoInteractions(orders);
        identity.setStatus(1);ReflectionTestUtils.setField(authentication,"wechatAppId","wx2222222222222222");
        businessFailure(request(get("/auth/client/info"),token,null),10001);
    }

    private void preparePlatform() {
        String app="wx1111111111111111";ReflectionTestUtils.setField(authentication,"wechatAppId",app);ReflectionTestUtils.setField(onboarding,"appId",app);
        var platform=new WechatService.WechatLoginResult();platform.setAppId(app);platform.setOpenId("IT_ONBOARDING_OPEN");platform.setSessionKey("IT_ONBOARDING_SESSION");
        when(wechat.jscode2session("prepare-code")).thenReturn(platform);
    }
    private String prepareOnboarding() throws Exception {
        preparePlatform();var result=mvc.perform(post("/auth/client/onboarding/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"prepare-code\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("PHONE_REQUIRED"))
            .andExpect(jsonPath("$.openId").doesNotExist()).andExpect(jsonPath("$.sessionKey").doesNotExist()).andReturn();
        String token=json.readTree(result.getResponse().getContentAsString()).path("tokenValue").asText();assertFalse(token.isBlank());return token;
    }
    @Test void temporaryWechatPhoneProofCannotBecomeCustomerOrReadAnyOldAccount() throws Exception {
        String token=prepareOnboarding();
        businessFailure(request(get("/api/client/orders/77"),token,null),10003);
        businessFailure(request(get("/auth/client/info"),token,null),10001);
        when(phoneAuthorization.authorize("wx1111111111111111","IT_ONBOARDING_OPEN","phone-code")).thenReturn(new com.agri_system.application.auth.service.WechatPhoneAuthorizationService.PhoneEvidence("wx1111111111111111","IT_ONBOARDING_OPEN","13800000000",java.time.Instant.now().getEpochSecond()));
        var result=mvc.perform(post("/auth/client/onboarding/phone").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"phone-code\",\"phone\":\"13900000000\",\"openid\":\"forged\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("PHONE_VERIFIED")).andExpect(jsonPath("$.phoneMasked").value("138****0000")).andExpect(jsonPath("$.phone").doesNotExist()).andExpect(jsonPath("$.customerId").doesNotExist()).andReturn();
        String verified=json.readTree(result.getResponse().getContentAsString()).path("tokenValue").asText();assertNotEquals(token,verified);
        mvc.perform(get("/auth/client/onboarding/info").header("Authorization",verified)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PHONE_VERIFIED")).andExpect(jsonPath("$.tokenValue").doesNotExist()).andExpect(jsonPath("$.openId").doesNotExist());
        mvc.perform(get("/auth/client/onboarding/info").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PHONE_REQUIRED"));
        businessFailure(request(get("/api/client/orders/77"),verified,null),10003);verifyNoInteractions(customers,wallets,orders);
        verify(identities,never()).save(any());
    }
    @Test void cancellingTemporaryFlowDuringPlatformCallCannotIssueAnotherProof() throws Exception {
        String token=prepareOnboarding();
        when(phoneAuthorization.authorize("wx1111111111111111","IT_ONBOARDING_OPEN","phone-code")).thenAnswer(call->{
            StpUtil.logoutByTokenValue(token);
            return new com.agri_system.application.auth.service.WechatPhoneAuthorizationService.PhoneEvidence("wx1111111111111111","IT_ONBOARDING_OPEN","13800000000",java.time.Instant.now().getEpochSecond());
        });
        businessFailure(request(post("/auth/client/onboarding/phone"),token,"{\"code\":\"phone-code\"}"),10001);
        verifyNoInteractions(customers,wallets,orders);verify(identities,never()).save(any());
    }
    @Test void expiredOrReconfiguredOnboardingAndOrdinaryCustomerCannotAuthorizePhone() throws Exception {
        String token=prepareOnboarding();ReflectionTestUtils.setField(onboarding,"appId","wx2222222222222222");
        businessFailure(request(post("/auth/client/onboarding/phone"),token,"{\"code\":\"phone-code\"}"),10001);
        ReflectionTestUtils.setField(onboarding,"appId","wx1111111111111111");
        StpUtil.getTokenSessionByToken(token).set("onboardingV1",Map.of("appId","wx1111111111111111","openId","IT_ONBOARDING_OPEN","state","PHONE_REQUIRED","expiresAt",1L));
        businessFailure(request(get("/auth/client/onboarding/info"),token,null),10001);
        businessFailure(request(post("/auth/client/onboarding/phone"),login("CUST_101"),"{\"code\":\"phone-code\"}"),10001);
        businessFailure(request(post("/auth/client/onboarding/phone"),login("STAFF_11"),"{\"code\":\"phone-code\"}"),10001);
        verifyNoInteractions(phoneAuthorization,wallets,orders);
    }
    @Test void existingVerifiedWechatBindingUsesOriginalLoginAndPersistedRevocationVersion() throws Exception {
        preparePlatform();var identity=new com.agri_system.domain.auth.model.CustomerIdentity();identity.setId(9L);identity.setCustomerId(101L);identity.setIdentityType("WECHAT_OPENID");identity.setAppId("wx1111111111111111");identity.setIdentityValue("IT_ONBOARDING_OPEN");identity.setStatus(1);identity.setAuthVersion(5L);
        when(identities.findWechatByAppAndOpenIdForUpdate("wx1111111111111111","IT_ONBOARDING_OPEN")).thenReturn(Optional.of(identity));when(identities.findById(9L)).thenReturn(Optional.of(identity));
        var result=mvc.perform(post("/auth/client/onboarding/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"prepare-code\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("LINKED")).andExpect(jsonPath("$.tokenValue").doesNotExist()).andReturn();
        String token=json.readTree(result.getResponse().getContentAsString()).path("login").path("tokenValue").asText();assertFalse(token.isBlank());
        mvc.perform(get("/auth/client/info").header("Authorization",token)).andExpect(status().isOk());
        identity.setAuthVersion(6L);businessFailure(request(get("/auth/client/info"),token,null),10001);verifyNoInteractions(phoneAuthorization);
    }
    @Test void oldQrCannotChangeCustomerOwnershipFromSelfReportedIdsOrOpenId() throws Exception {
        businessFailure(request(post("/api/client/auth/qrBind"),login("CUST_101"),"{\"qrToken\":\"TEST-QR\",\"customerId\":202,\"openid\":\"FAKE\"}"),10001);
        verifyNoInteractions(oldQr);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "detail", "preview", "pay", "intent", "confirm", "receipt", "apply", "cancel"})
    void unauthenticatedCustomerRoutesRejectBeforeBusiness(String route) throws Exception {
        businessFailure(call(route, null), 10003);
        verifyNoInteractions(orders, afterSales, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "detail", "preview", "pay", "intent", "confirm", "receipt", "apply", "cancel"})
    void staffCannotUseCustomerRoutesEvenWithForgedCustomerHeader(String route) throws Exception {
        businessFailure(call(route, login("STAFF_1")), 10003);
        verifyNoInteractions(orders, afterSales, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUST_303", "CUST_404"})
    void disabledAndMissingCustomersRejectBeforeBusiness(String id) throws Exception {
        businessFailure(call("create", login(id)), 10001)
                .andExpect(jsonPath("$.message").value(id.endsWith("303") ? "客户账号已停用" : "客户不存在"));
        verifyNoInteractions(orders, afterSales);
    }

    @Test void createUsesAuthenticatedCustomerInsteadOfHeaderOrBodyIdentity() throws Exception {
        when(orders.createOrder(any(), eq(101L))).thenReturn(77L);
        call("create", login("CUST_101")).andExpect(status().isOk()).andExpect(content().string("77"));
        var command = ArgumentCaptor.forClass(OrderCreateCommand.class);
        verify(orders).createOrder(command.capture(), eq(101L));
        assertEquals(202L, command.getValue().getCustomerId(), "客户端字段仍在命令中，可信身份必须另传");
        verifyNoMoreInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"preview", "pay", "intent"})
    void paymentRoutesOverrideSubmittedOrderIdAndUseAuthenticatedCustomer(String route) throws Exception {
        call(route, login("CUST_101")).andExpect(status().isOk());
        var command = ArgumentCaptor.forClass(OrderPayCommand.class);
        switch (route) {
            case "preview" -> verify(orders).previewOrderPayment(command.capture(), eq(101L));
            case "pay" -> verify(orders).payOrder(command.capture(), eq(101L));
            case "intent" -> verify(orders).createExternalPaymentIntent(command.capture(), eq(101L));
            default -> fail("未知支付入口");
        }
        assertEquals(77L, command.getValue().getOrderId());
        assertFalse(command.getValue().getUseBalance());
        verifyNoMoreInteractions(orders); // 预览/意图均不能绕到确认、付款或公开回调方法。
    }

    @ParameterizedTest
    @ValueSource(strings = {"preview", "pay", "intent"})
    void paymentBodyDoesNotNeedToRepeatThePathOrderId(String route) throws Exception {
        request(post(paymentPath(route)), login("CUST_101"), "{}").andExpect(status().isOk());
        var command = ArgumentCaptor.forClass(OrderPayCommand.class);
        switch (route) {
            case "preview" -> verify(orders).previewOrderPayment(command.capture(), eq(101L));
            case "pay" -> verify(orders).payOrder(command.capture(), eq(101L));
            case "intent" -> verify(orders).createExternalPaymentIntent(command.capture(), eq(101L));
            default -> fail("未知支付入口");
        }
        assertEquals(77L, command.getValue().getOrderId());
        verifyNoMoreInteractions(orders);
    }

    @Test void paymentPreviewReturnsBusinessServicePreviewWithoutConfirmingPayment() throws Exception {
        OrderPayPreviewVO preview = new OrderPayPreviewVO();
        preview.setExternalAmount(new java.math.BigDecimal("12.34"));
        when(orders.previewOrderPayment(any(), eq(101L))).thenReturn(preview);
        call("preview", login("CUST_101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.externalAmount").value(12.34));
        verify(orders).previewOrderPayment(any(), eq(101L));
        verifyNoMoreInteractions(orders);
    }

    @Test void confirmationPassesIntentAndCustomerWithoutCallingPublicCallback() throws Exception {
        call("confirm", login("CUST_101")).andExpect(status().isOk());
        var command = ArgumentCaptor.forClass(OrderExternalPaymentConfirmCommand.class);
        verify(orders).confirmExternalPayment(command.capture(), eq(101L));
        assertEquals("PI-test", command.getValue().getIntentNo());
        assertEquals(77L, command.getValue().getOrderId(), "支付确认必须绑定URL中的订单");
        assertEquals("wx-test", command.getValue().getExternalTransactionNo());
        verifyNoMoreInteractions(orders);
    }

    @Test void detailAndReceiptUseAuthenticatedCustomer() throws Exception {
        String token = login("CUST_101");
        call("detail", token).andExpect(status().isOk());
        call("receipt", token).andExpect(status().isOk());
        verify(orders).getOrderDetailForCustomer(77L, 101L);
        verify(orders).confirmReceiptForCustomer(77L, 101L);
        verifyNoMoreInteractions(orders);
    }

    @Test void afterSalesApplyAndCancelUseAuthenticatedCustomer() throws Exception {
        when(afterSales.applyFromClient(any(), eq(101L))).thenReturn(88L);
        String token = login("CUST_101");
        call("apply", token).andExpect(status().isOk()).andExpect(content().string("88"));
        call("cancel", token).andExpect(status().isOk());
        var command = ArgumentCaptor.forClass(ClientApplyCommand.class);
        verify(afterSales).applyFromClient(command.capture(), eq(101L));
        assertEquals(77L, command.getValue().getOrderId());
        assertEquals("不再需要", command.getValue().getReason());
        verify(afterSales).cancel(88L, 101L);
        verifyNoMoreInteractions(afterSales);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"storeId\":1,\"orderType\":2,\"items\":[]}",
            "{\"storeId\":1,\"orderType\":2,\"items\":[{\"productId\":11}]}",
            "{\"storeId\":1,\"orderType\":2,\"items\":[{\"productId\":11,\"quantity\":0}]}",
            "{\"storeId\":1,\"orderType\":2,\"items\":[{\"productId\":11,\"quantity\":-1}]}",
            "{\"storeId\":\"invalid\"}"})
    void invalidCreateBodyNeverReachesBusinessService(String body) throws Exception {
        businessFailure(request(post("/api/client/orders"), login("CUST_101"), body), 10001);
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"intentNo\":\"PI-test\"}", "{\"externalTransactionNo\":\"wx-test\"}"})
    void invalidConfirmationNeverReachesBusinessService(String body) throws Exception {
        businessFailure(request(post("/api/client/orders/77/payment-confirmation"), login("CUST_101"), body), 10001);
        verifyNoInteractions(orders);
    }

    @Test void invalidAfterSalesBodyNeverReachesBusinessService() throws Exception {
        businessFailure(request(post("/api/client/after-sales/apply"), login("CUST_101"), "{}"), 10001);
        verifyNoInteractions(afterSales);
    }

    @Test void businessRejectionIsReturnedAsBusinessCodeAndNotSuccessfulPayment() throws Exception {
        when(orders.previewOrderPayment(any(), eq(101L))).thenThrow(new IllegalArgumentException("无权操作此订单"));
        // 只验证异常HTTP映射，真正的客户归属拒绝另由真实应用服务/数据库测试证明。
        businessFailure(call("preview", login("CUST_101")), 10001)
                .andExpect(jsonPath("$.message").value("无权操作此订单"));
        verify(orders).previewOrderPayment(any(), eq(101L));
        verifyNoMoreInteractions(orders);
    }

    @Test void callbackWithoutConfiguredSecretIsDisabled() throws Exception {
        ReflectionTestUtils.setField(callbacks, "callbackSecret", "");
        request(post("/api/public/orders/payments/confirm"), null, CONFIRM)
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(orders, customers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "错误密钥"})
    void callbackMissingOrWrongSecretRejectsBeforeBusiness(String secret) throws Exception {
        var request = post("/api/public/orders/payments/confirm");
        if (!secret.isEmpty()) request.header("X-Payment-Secret", secret);
        request(request, null, CONFIRM).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders, customers);
    }

    @Test void callbackAcceptsConfiguredSecretWithoutCustomerLoginAndPassesPayload() throws Exception {
        request(post("/api/public/orders/payments/confirm").header("X-Payment-Secret", "测试回调密钥"), null, CONFIRM)
                .andExpect(status().isOk()).andExpect(content().string("success"));
        var command = ArgumentCaptor.forClass(OrderExternalPaymentCallbackCommand.class);
        verify(orders).confirmExternalPaymentCallback(command.capture());
        assertEquals("PI-test", command.getValue().getIntentNo());
        assertEquals("wx-test", command.getValue().getExternalTransactionNo());
        verifyNoMoreInteractions(orders);
        verifyNoInteractions(customers);
    }

    @Test void callbackInvalidPayloadNeverReachesBusinessEvenWithCorrectSecret() throws Exception {
        businessFailure(request(post("/api/public/orders/payments/confirm")
                .header("X-Payment-Secret", "测试回调密钥"), null, "{}"), 10001);
        verifyNoInteractions(orders);
    }

    private ResultActions call(String route, String token) throws Exception {
        return switch (route) {
            case "create" -> request(post("/api/client/orders"), token, CREATE);
            case "detail" -> request(get("/api/client/orders/77"), token, null);
            case "preview", "pay", "intent" -> request(post(paymentPath(route)), token, PAY);
            case "confirm" -> request(post("/api/client/orders/77/payment-confirmation"), token, CONFIRM);
            case "receipt" -> request(post("/api/client/orders/77/confirm-receipt"), token, null);
            case "apply" -> request(post("/api/client/after-sales/apply"), token,
                    "{\"orderId\":77,\"reason\":\"不再需要\",\"customerId\":202}");
            case "cancel" -> request(post("/api/client/after-sales/88/cancel"), token, null);
            default -> throw new AssertionError("未知入口：" + route);
        };
    }

    private static String paymentPath(String route) {
        return "/api/client/orders/77/" + switch (route) {
            case "preview" -> "payment-preview";
            case "intent" -> "payment-intent";
            case "pay" -> "pay";
            default -> throw new AssertionError("未知支付入口：" + route);
        };
    }

    private ResultActions request(MockHttpServletRequestBuilder request, String token, String body) throws Exception {
        request.header("X-Customer-Id", "202");
        if (token != null) request.header(SaManager.getConfig().getTokenName(), token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private String login(String loginId) throws Exception {
        var result = mvc.perform(get("/auth/__client_test__/login").param("id", loginId))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions businessFailure(ResultActions result, int code) throws Exception {
        return result.andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
    }

    @AfterAll static void restoreSaTokenGlobals() {
        SaManager.setStpInterface(OLD_STP);
        SaManager.setSaTokenDao(OLD_DAO);
        SaManager.setSaTokenContext(OLD_CONTEXT);
        SaManager.setConfig(OLD_CONFIG);
    }
}
