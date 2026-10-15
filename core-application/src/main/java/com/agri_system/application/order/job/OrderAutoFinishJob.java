package com.agri_system.application.order.job;

import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.domain.order.repository.OrderRepository;
import com.agri_system.domain.order.repository.OrderDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

/** 快递按实际发货时间自动签收；同城由员工或客户人工签收。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoFinishJob {
    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository deliveryRepository;
    private final OrderApplicationService orderService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void autoFinishOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(10);
        int count = 0;
        for (var order : orderRepository.findShippedOrdersBefore(cutoff)) {
            var delivery = deliveryRepository.findByOrderId(order.getId()).orElse(null);
            if (delivery == null || !Integer.valueOf(3).equals(delivery.getDeliveryType())
                    || !Integer.valueOf(3).equals(delivery.getDeliveryStatus())
                    || delivery.getDeliveryAt() == null || !delivery.getDeliveryAt().isBefore(cutoff)) continue;
            try {
                // 每单独立事务，统一入口重新锁单及核验售后、剩余物料。
                orderService.confirmReceipt(order.getId(), null);
                count++;
            } catch (IllegalArgumentException ex) {
                log.info("订单 {} 暂不能自动签收：{}", order.getId(), ex.getMessage());
            }
        }
        log.info("快递自动签收完成，共处理 {} 单", count);
    }
}
