package com.agri_system.application.notification.service;

import com.agri_system.application.notification.config.WechatSubscribeMessageProperties;
import com.agri_system.application.notification.config.WechatSubscribeMessageProperties.Template;
import com.agri_system.application.notification.service.WechatSubscribeMessageService.SendResult;
import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 客户微信通知器门控逻辑 —— 验证"未配置即 no-op、失败不抛错"的门控承诺。
 */
@DisplayName("客户微信通知器门控逻辑")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerWechatNotifierTest {

    @Test void foreignDisabledOrAmbiguousWechatIdentityIsNeverChosenForNotification() {
        properties.setTemplates(Map.of("order-finished",template("TPL_1","p",Map.of("thing1","orderNo"))));
        var i=new CustomerIdentity();i.setCustomerId(1L);i.setIdentityType("WECHAT_OPENID");i.setAppId("wx2222222222222222");i.setStatus(1);i.setIdentityValue("OTHER_APP");
        when(identityRepository.findActiveWechatByCustomerAndApp(1L,"wx1111111111111111")).thenReturn(List.of(i));
        notifier.notify("order-finished",1L,Map.of());i.setAppId("wx1111111111111111");i.setStatus(2);notifier.notify("order-finished",1L,Map.of());
        i.setStatus(1);when(identityRepository.findActiveWechatByCustomerAndApp(1L,"wx1111111111111111")).thenReturn(List.of(i,i));notifier.notify("order-finished",1L,Map.of());
        verifyNoInteractions(messageService);
    }

    @Mock CustomerIdentityRepository identityRepository;
    @Mock WechatSubscribeMessageService messageService;

    private WechatSubscribeMessageProperties properties;
    private CustomerWechatNotifier notifier;

    @BeforeEach
    void setUp() {
        properties = new WechatSubscribeMessageProperties();
        properties.setEnabled(true);
        properties.setMiniprogramState("formal");
        notifier = new CustomerWechatNotifier(properties, identityRepository, messageService);
        org.springframework.test.util.ReflectionTestUtils.setField(notifier,"appId","wx1111111111111111");
    }

    private Template template(String id, String page, Map<String, String> fields) {
        Template t = new Template();
        t.setTemplateId(id);
        t.setPage(page);
        t.setFields(fields);
        return t;
    }

    private void bindOpenid(Long customerId, String openid) {
        CustomerIdentity identity = new CustomerIdentity();
        identity.setIdentityValue(openid);identity.setCustomerId(customerId);identity.setAppId("wx1111111111111111");identity.setIdentityType("WECHAT_OPENID");identity.setStatus(1);
        when(identityRepository.findActiveWechatByCustomerAndApp(customerId,"wx1111111111111111"))
                .thenReturn(List.of(identity));
    }

    @Test
    @DisplayName("总开关关闭：完全不触发下游")
    void disabledNoOp() {
        properties.setEnabled(false);
        notifier.notify("order-finished", 1L, Map.of("orderNo", "NO1"));
        verifyNoInteractions(identityRepository, messageService);
    }

    @Test
    @DisplayName("事件未配置模板：静默跳过")
    void noTemplateSkipped() {
        notifier.notify("unknown-event", 1L, Map.of());
        verifyNoInteractions(identityRepository, messageService);
    }

    @Test
    @DisplayName("模板 templateId 为空：静默跳过")
    void blankTemplateIdSkipped() {
        properties.setTemplates(Map.of("order-finished", template("", "p", Map.of())));
        notifier.notify("order-finished", 1L, Map.of());
        verifyNoInteractions(identityRepository, messageService);
    }

    @Test
    @DisplayName("customerId 为空（散客）：跳过")
    void nullCustomerSkipped() {
        properties.setTemplates(Map.of("order-finished",
                template("TPL_1", "p", Map.of("thing1", "orderNo"))));
        notifier.notify("order-finished", null, Map.of("orderNo", "NO1"));
        verifyNoInteractions(identityRepository, messageService);
    }

    @Test
    @DisplayName("客户未绑微信(openid 缺失)：跳过且不发送")
    void noOpenidSkipped() {
        properties.setTemplates(Map.of("order-finished",
                template("TPL_1", "p", Map.of("thing1", "orderNo"))));
        when(identityRepository.findActiveWechatByCustomerAndApp(1L,"wx1111111111111111"))
                .thenReturn(List.of());
        notifier.notify("order-finished", 1L, Map.of("orderNo", "NO1"));
        verify(messageService, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("正常发送：按 fields 映射组装数据 + 渲染 page 占位符")
    void happyPath() {
        Template t = template("TPL_1", "pages/order/detail?id={orderId}",
                Map.of("thing1", "orderNo", "amount2", "amount", "date3", "time"));
        properties.setTemplates(Map.of("order-finished", t));
        bindOpenid(1L, "OPENID_X");
        when(messageService.send(eq("OPENID_X"), eq("TPL_1"), anyString(), anyMap(), eq("formal")))
                .thenReturn(SendResult.ok());

        notifier.notify("order-finished", 1L, Map.of(
                "orderId", "42", "orderNo", "NO1", "amount", "99.50", "time", "2026-08-09"));

        ArgumentCaptor<Map<String, String>> dataCap = ArgumentCaptor.forClass(Map.class);
        verify(messageService).send(eq("OPENID_X"), eq("TPL_1"),
                eq("pages/order/detail?id=42"), dataCap.capture(), eq("formal"));
        Map<String, String> data = dataCap.getValue();
        assertEquals("NO1", data.get("thing1"));
        assertEquals("99.50", data.get("amount2"));
        assertEquals("2026-08-09", data.get("date3"));
    }

    @Test
    @DisplayName("用户未订阅(43101)：视为跳过，不抛异常")
    void userNotSubscribedSkipped() {
        properties.setTemplates(Map.of("order-finished",
                template("TPL_1", "p", Map.of("thing1", "orderNo"))));
        bindOpenid(1L, "OPENID_X");
        when(messageService.send(any(), any(), any(), any(), any()))
                .thenReturn(SendResult.of(43101, "refuse"));
        assertDoesNotThrow(() -> notifier.notify("order-finished", 1L, Map.of("orderNo", "NO1")));
    }

    @Test
    @DisplayName("send 抛异常：通知器吞掉，不向上传播")
    void sendExceptionSwallowed() {
        properties.setTemplates(Map.of("order-finished",
                template("TPL_1", "p", Map.of("thing1", "orderNo"))));
        bindOpenid(1L, "OPENID_X");
        when(messageService.send(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("network down"));
        assertDoesNotThrow(() -> notifier.notify("order-finished", 1L, Map.of("orderNo", "NO1")));
    }
}
