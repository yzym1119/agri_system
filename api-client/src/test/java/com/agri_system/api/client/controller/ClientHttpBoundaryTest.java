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
        "spring.flyway.enabled=false", "sa-token.is-print=false", "payment.callback.secret=测试回调密钥"})
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SaTokenConfigure.class, AuthService.class, GlobalExceptionHandler.class,
            OrderClientController.class, AfterSalesClientController.class,
            OrderPaymentPublicController.class, LoginProbe.class})
    static class Config {
        @Bean SaTokenDao saTokenDao() { return new SaTokenDaoDefaultImpl(); }
    }

    @RestController
    static class LoginProbe {
        @GetMapping("/auth/__client_test__/login")
        Map<String, String> login(@RequestParam String id) {
            StpUtil.login(id);
            return Map.of("token", StpUtil.getTokenValue());
        }
    }

    @BeforeEach void customerAndCallback() {
        Customer active = new Customer(); active.setId(101L); active.setStatus(1);
        Customer disabled = new Customer(); disabled.setId(303L); disabled.setStatus(2);
        when(customers.findById(101L)).thenReturn(Optional.of(active));
        when(customers.findById(303L)).thenReturn(Optional.of(disabled));
        ReflectionTestUtils.setField(callbacks, "callbackSecret", "测试回调密钥");
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
