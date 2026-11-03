package com.agri_system.domain.order.query;

import java.time.LocalDateTime;
import java.util.List;

/** 本店提货列表最小只读事实；不含客户、金额、成本或确认能力。 */
public interface PickupTaskQuery {
    record Task(Long requestId, String orderNo, String productName, String status, LocalDateTime createdAt, LocalDateTime fulfilledAt) {}

    /** 当前身份及输入由应用验证，按门店/状态倒序最多21项，after为排他游标。 */
    List<Task> findTasks(Long storeId, String state, Long after);
}
