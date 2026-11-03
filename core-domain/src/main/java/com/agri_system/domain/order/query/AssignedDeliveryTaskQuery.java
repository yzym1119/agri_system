package com.agri_system.domain.order.query;

import java.time.LocalDateTime;
import java.util.List;

/** 本人本店人工配送的最小只读事实；不返回收货联系人、资金或成本。 */
public interface AssignedDeliveryTaskQuery {
    record Task(Long orderId, String orderNo, LocalDateTime deliveryAt, LocalDateTime receivedAt) {}

    /** 已验证真实员工门店后调用，倒序最多21项（页面20项加一项探测）；after为排他游标。 */
    List<Task> findAssigned(Long storeId, Long staffId, int deliveryStatus, Long after);
}
