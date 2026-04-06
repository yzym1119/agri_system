package com.agri_system.application.performance.event;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 订单退款事件
 * refundItems: key=itemId, value=退款数量
 */
public record OrderRefundedEvent(Long orderId,
                                 BigDecimal refundAmount,
                                 Map<Long, BigDecimal> refundItems) {
}

