package com.agri_system.application.notification.service;

import com.agri_system.application.notification.config.WechatSubscribeMessageProperties;
import com.agri_system.application.notification.config.WechatSubscribeMessageProperties.Template;
import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.application.notification.service.WechatSubscribeMessageService.SendResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户微信订阅消息通知器（门控 + 编排 + 永不抛异常）。
 * <p>
 * 将"事件语义数据"翻译成微信模板数据后交给 {@link WechatSubscribeMessageService} 发送。
 * 任何未配置（总开关关 / 事件无模板 / 客户未绑微信）或发送失败的情况都静默降级，
 * 通知是副作用，绝不回滚或影响业务事务。
 *
 * @author AI Generated
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerWechatNotifier {

    static final String IDENTITY_WECHAT_OPENID = "WECHAT_OPENID";

    private final WechatSubscribeMessageProperties properties;
    private final CustomerIdentityRepository identityRepository;
    private final WechatSubscribeMessageService messageService;

    /**
     * 向客户发送一条微信订阅消息（门控 + 永不抛异常）。
     *
     * @param eventKey     事件键（对应配置 templates 中的 key，如 order-finished）
     * @param customerId   客户ID
     * @param semanticData 语义数据（由事件监听器提供，如 orderNo / amount / time）
     */
    public void notify(String eventKey, Long customerId, Map<String, String> semanticData) {
        if (!properties.isEnabled()) {
            return;
        }
        if (customerId == null) {
            return;
        }
        Template template = properties.getTemplates() == null
                ? null : properties.getTemplates().get(eventKey);
        if (template == null || template.getTemplateId() == null || template.getTemplateId().isBlank()) {
            return; // 未配置模板 —— 静默跳过
        }

        String openid = resolveOpenid(customerId);
        if (openid == null) {
            return; // 客户未绑定微信 —— 静默跳过
        }

        Map<String, String> data = buildData(template, semanticData);
        String page = renderPage(template.getPage(), semanticData);
        try {
            SendResult result = messageService.send(
                    openid, template.getTemplateId(), page, data, properties.getMiniprogramState());
            if (result.isSuccess()) {
                log.debug("微信订阅消息已发送: event={} customerId={}", eventKey, customerId);
            } else if (result.isSkipped()) {
                log.debug("微信订阅消息跳过(用户未订阅): event={} customerId={}", eventKey, customerId);
            } else {
                log.warn("微信订阅消息发送失败: event={} customerId={} errcode={} msg={}",
                        eventKey, customerId, result.getErrcode(), result.getMessage());
            }
        } catch (Exception e) {
            // 通知是副作用，绝不影响主业务
            log.warn("微信订阅消息发送异常: event={} customerId={} err={}",
                    eventKey, customerId, e.getMessage());
        }
    }

    private String resolveOpenid(Long customerId) {
        return identityRepository.findByCustomerIdAndType(customerId, IDENTITY_WECHAT_OPENID)
                .map(CustomerIdentity::getIdentityValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(null);
    }

    /** 按模板 fields 映射，把语义数据转成微信模板字段 → 值。缺失的语义值跳过该字段。 */
    private Map<String, String> buildData(Template template, Map<String, String> semanticData) {
        Map<String, String> data = new LinkedHashMap<>();
        if (template.getFields() == null || semanticData == null) {
            return data;
        }
        template.getFields().forEach((wechatField, semanticKey) -> {
            String value = semanticData.get(semanticKey);
            if (value != null) {
                data.put(wechatField, value);
            }
        });
        return data;
    }

    /** 用语义数据渲染 page 占位符（如 {orderId}）。 */
    private String renderPage(String page, Map<String, String> semanticData) {
        if (page == null || page.isBlank() || semanticData == null) {
            return page;
        }
        String rendered = page;
        for (Map.Entry<String, String> e : semanticData.entrySet()) {
            if (e.getValue() != null) {
                rendered = rendered.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return rendered;
    }
}
