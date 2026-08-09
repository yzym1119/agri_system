package com.agri_system.application.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 微信订阅消息（小程序）通知配置。
 * <p>
 * 管道默认 no-op：{@code enabled=false} 或某事件的 {@code templateId} 留空时整条链路静默跳过，
 * 不抛错、不影响业务事务。运营在 mp.weixin.qq.com 申请模板并配置 template-id / 字段映射后即激活，
 * 无需再改后端代码。
 *
 * @author AI Generated
 */
@Data
@ConfigurationProperties(prefix = "wechat.subscribe-message")
public class WechatSubscribeMessageProperties {

    /** 总开关：false 时整条客户微信通知管道 no-op。 */
    private boolean enabled = false;

    /** 小程序版本状态：formal(正式) / developer(开发) / trial(体验)。 */
    private String miniprogramState = "formal";

    /**
     * 事件 → 模板配置。key 为事件键（如 order-finished / recharge-success），
     * 与 {@code CustomerNotificationEventListener} 中写死的事件键一一对应。
     */
    private Map<String, Template> templates = Map.of();

    @Data
    public static class Template {
        /** 模板ID（mp.weixin.qq.com 申请后填入；为空则该事件跳过）。 */
        private String templateId;
        /** 跳转页面，支持 {orderId} 等占位符，用语义数据渲染。 */
        private String page;
        /** 字段映射：微信模板字段(如 thing1) → 语义键(如 orderNo)；语义键由事件监听器提供。 */
        private Map<String, String> fields = Map.of();
    }
}
