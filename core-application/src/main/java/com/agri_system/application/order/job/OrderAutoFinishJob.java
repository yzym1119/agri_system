package com.agri_system.application.order.job;

import com.agri_system.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 订单自动收货定时任务
 * 扫描已发货超过10天的订单，自动标记为已完成
 * 
 * @author AI Generated
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoFinishJob {
    
    private final OrderRepository orderRepository;
    
    /**
     * 自动收货任务
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void autoFinishOrders() {
        log.info("开始执行订单自动收货任务");
        
        // 查询已发货超过10天的订单
        LocalDateTime tenDaysAgo = LocalDateTime.now().minusDays(10);
        java.util.List<com.agri_system.domain.order.model.Order> orders = 
                orderRepository.findShippedOrdersBefore(tenDaysAgo);
        
        // 标记为已完成
        int count = 0;
        for (com.agri_system.domain.order.model.Order order : orders) {
            if (order.getOrderStatus() != null && order.getOrderStatus() == 5) { // 已发货
                order.markAsFinished();
                orderRepository.save(order);
                count++;
                log.info("订单 {} 自动收货完成", order.getOrderNo());
            }
        }
        log.info("本次自动收货任务完成，共处理 {} 个订单", count);
        
        log.info("订单自动收货任务执行完成");
    }
}




