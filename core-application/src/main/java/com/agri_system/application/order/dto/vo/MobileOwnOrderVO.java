package com.agri_system.application.order.dto.vo;

import java.util.List;

/** 手机本人订单只读白名单；Long/十进制保持字符串，缺资料保持null。 */
public final class MobileOwnOrderVO {
    private MobileOwnOrderVO() {}
    public record Finance(String payAmount,String receivedAmount,String creditAmount,String creditReductionAmount,
            String cashRefundedAmount,String remainingCreditAmount,String repaidAmount) {}
    public record Summary(String orderId,String orderNo,String createdAt,Integer state,String customerLabel,
            Finance finance,Integer deliveryType,Integer deliveryStatus) {}
    public record Orders(String storeId,List<Summary> orders,String nextCursor) {}
    public record Item(String itemId,String productName,String quantity,String unit,String totalPrice,
            String refundedQuantity,String refundedAmount,Integer packageGroupNo,String packageName,
            String packageQuantity,String packageUnitPrice) {}
    public record Detail(String storeId,Summary order,List<Item> items) {}
}
