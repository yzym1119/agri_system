package com.agri_system.application.order.dto.vo;
import java.math.BigDecimal;
import java.util.List;
import com.agri_system.application.order.service.OrderPaymentService.RefundSourceView;
public record OrderRefundPreviewVO(String quoteId,Long orderId,BigDecimal refundAmount,
        BigDecimal creditReductionAmount,BigDecimal cashRefundAmount,BigDecimal remainingDebt,
        BigDecimal remainingRefundableAmount,List<Item> items,List<RefundSourceView> sources) {
    public record Item(Long orderItemId,BigDecimal quantity,BigDecimal refundAmount,
                       BigDecimal refundableQuantity,BigDecimal refundableAmount) {}
}
