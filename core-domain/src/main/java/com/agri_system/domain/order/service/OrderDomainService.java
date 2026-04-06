package com.agri_system.domain.order.service;

import com.agri_system.domain.order.model.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * 订单领域服务
 * 负责订单相关的业务规则
 * 
 * @author AI Generated
 */
public class OrderDomainService {
    
    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    /**
     * 生成订单号
     * 格式：YYYYMMDDHHMMSS + 4位随机数
     * 
     * @return 订单号
     */
    public String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FORMATTER);
        int random = RANDOM.nextInt(10000);
        return timestamp + String.format("%04d", random);
    }
    
    /**
     * 生成自提码（6位数字）
     * 
     * @return 自提码
     */
    public String generatePickupCode() {
        int code = RANDOM.nextInt(900000) + 100000; // 100000-999999
        return String.valueOf(code);
    }
    
    /**
     * 计算订单中专用金可用最大额度
     * 
     * 规则：仅 allow_special_fund = 1 的商品金额，才可纳入专用金抵扣额度计算
     * 
     * @param items 订单明细列表
     * @return 专用金可用最大额度
     */
    public BigDecimal calculateMaxSpecialFundAmount(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        return items.stream()
                .filter(item -> item.getAllowSpecialFund() != null && item.getAllowSpecialFund() == 1)
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 校验订单金额是否一致
     * 
     * @param totalAmount 订单总金额
     * @param items 订单明细列表
     * @return 是否一致
     */
    public boolean validateOrderAmount(BigDecimal totalAmount, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return totalAmount.compareTo(BigDecimal.ZERO) == 0;
        }
        
        BigDecimal calculatedTotal = items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return totalAmount.compareTo(calculatedTotal) == 0;
    }
}








