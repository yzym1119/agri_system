package com.agri_system.domain.order.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本店提货订单级只读事实；不含金额、成本或确认能力。
 *
 * <p>提货请求仍按订单明细存在，这里只为手机列表按原订单聚合；客户只返回姓名原文，
 * 脱敏属于展示规则，由应用层决定，查询端口不做裁剪。</p>
 */
public interface PickupTaskQuery {
    /** 一条提货请求行；customerName 取自原订单客户档案，散客为 null。 */
    record OrderLine(Long requestId, Long orderId, String orderNo, String customerName,
                     String productName, String status, LocalDateTime createdAt, LocalDateTime fulfilledAt) {}

    /**
     * 按门店与状态定位原订单（每页最多 21 个订单，orderId 倒序），并返回这些订单的
     * <b>全部</b>提货请求行（含另一状态的项），用于统计整单待交/已交项数。
     *
     * <p>after 是排他游标，比较订单 ID 而不是请求 ID，订单边界不会截断同一订单的明细行。</p>
     */
    List<OrderLine> findOrderLines(Long storeId, String state, Long after);

    /** 本店指定原单的全部提货请求；不受列表页游标或状态裁剪。 */
    List<OrderLine> findOrderLinesForOrder(Long storeId, Long orderId);
}
