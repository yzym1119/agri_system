package com.agri_system.application.order.dto.vo;

import java.util.List;

/** 本人配送专用视图，不附金额、成本或其他订单/员工资料。 */
public final class AssignedDeliveryVO {
    private AssignedDeliveryVO() {}
    public record Task(String orderId,String orderNo,String status,String deliveryAt,String receivedAt) {}
    public record Tasks(String storeId,String state,List<Task> tasks,String nextCursor) {}
    public record Item(String orderItemId,String productName,String quantity,String unit) {}
    public record Preview(String storeId,String orderId,String orderNo,String status,boolean canConfirm,String blockReason,
                          String receiverName,String receiverPhone,String receiverAddress,String confirmationKey,List<Item> items,String receivedAt) {}
    public record Result(String storeId,String orderId,String status,String receivedAt) {}
}
