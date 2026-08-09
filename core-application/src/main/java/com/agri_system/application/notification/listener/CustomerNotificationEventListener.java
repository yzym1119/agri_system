package com.agri_system.application.notification.listener;

import com.agri_system.application.notification.config.WechatSubscribeMessageProperties;
import com.agri_system.application.notification.service.CustomerWechatNotifier;
import com.agri_system.application.performance.event.CustomerRechargeEvent;
import com.agri_system.application.performance.event.OrderFinishedEvent;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户微信通知事件接线。
 * <p>
 * 监听业务事件（订单完成 / 充值成功），在事务提交后异步触发微信订阅消息。
 * AFTER_COMMIT 保证业务已落库才通知；@Async 保证微信网络调用不阻塞/不回滚业务事务。
 * 事件键 order-finished / recharge-success 与 yml 中 templates 配置一一对应。
 *
 * @author AI Generated
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerNotificationEventListener {

    /** 事件键 —— 与配置 wechat.subscribe-message.templates 中的 key 对应。 */
    static final String EVENT_ORDER_FINISHED = "order-finished";
    static final String EVENT_RECHARGE_SUCCESS = "recharge-success";

    private final WechatSubscribeMessageProperties properties;
    private final CustomerWechatNotifier notifier;
    private final OrderRepository orderRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderFinished(OrderFinishedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        Long orderId = event.orderId();
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getCustomerId() == null) {
            return;
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("orderId", String.valueOf(orderId));
        if (order.getOrderNo() != null) {
            data.put("orderNo", order.getOrderNo());
        }
        BigDecimal pay = order.getPayAmount();
        data.put("amount", pay == null ? "0" : pay.toPlainString());
        data.put("time", order.getCreatedAt() == null ? "" : order.getCreatedAt().toString());
        notifier.notify(EVENT_ORDER_FINISHED, order.getCustomerId(), data);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCustomerRecharge(CustomerRechargeEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        if (event.customerId() == null) {
            return;
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("customerId", String.valueOf(event.customerId()));
        BigDecimal amount = event.amount();
        data.put("amount", amount == null ? "0" : amount.toPlainString());
        notifier.notify(EVENT_RECHARGE_SUCCESS, event.customerId(), data);
    }
}
