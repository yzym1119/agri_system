package com.agri_system.application.order.service;

import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.agri_system.domain.order.enums.PaymentMethod;
import com.agri_system.domain.order.model.Order;
import com.agri_system.domain.order.model.OrderPayment;
import java.math.BigDecimal;
import java.util.List;

/** 列表与详情共用的原支付展示口径；无仓储、扣款、退款或核销能力。 */
final class OrderPaymentDisplayPolicy {
    private OrderPaymentDisplayPolicy() {}
    static PaymentSummary summarizePayment(Order order, List<OrderPayment> payments) {
        BigDecimal receivedAmount = BigDecimal.ZERO;
        BigDecimal creditAmount = BigDecimal.ZERO;

        if (payments != null && !payments.isEmpty()) {
            for (OrderPayment payment : payments) {
                if (payment == null || payment.getAmount() == null) {
                    continue;
                }
                Integer paymentType = payment.getPaymentType();
                if (paymentType != null && paymentType == PaymentMethod.COUPON.getCode()) {
                    continue;
                }
                if (paymentType != null && paymentType == PaymentMethod.CREDIT.getCode()) {
                    creditAmount = creditAmount.add(payment.getAmount());
                } else {
                    receivedAmount = receivedAmount.add(payment.getAmount());
                }
            }
            return new PaymentSummary(receivedAmount, creditAmount);
        }

        BigDecimal netReceivable = safeSubtract(
                safeSubtract(order.getTotalAmount(), order.getDiscountAmount()),
                order.getCouponAmount()
        );

        if (order.getPaymentMethod() != null && order.getPaymentMethod() == OrderMainPaymentMethod.CREDIT.getCode()) {
            return new PaymentSummary(BigDecimal.ZERO, netReceivable);
        }

        receivedAmount = defaultAmount(order.getPayAmount());
        creditAmount = safeSubtract(netReceivable, receivedAmount);
        return new PaymentSummary(receivedAmount, creditAmount);
    }

    static String resolveMainPaymentMethodName(Integer paymentMethod) {
        if (paymentMethod == null) {
            return null;
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.CASH.getCode())) {
            return "现金";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.WECHAT.getCode())) {
            return "微信";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.ALIPAY.getCode())) {
            return "支付宝";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.BALANCE.getCode())) {
            return "本金余额";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.COUPON.getCode())) {
            return "优惠券";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.CREDIT.getCode())) {
            return "赊销";
        }
        if (paymentMethod.equals(OrderMainPaymentMethod.MIXED.getCode())) {
            return "混合支付";
        }
        return "未知";
    }

    static String resolvePaymentRecordDisplayName(OrderPayment payment) {
        Integer paymentType = payment.getPaymentType();
        if (paymentType == null) {
            return "未知";
        }
        if (paymentType.equals(PaymentMethod.CASH.getCode())) {
            return "现金";
        }
        if (paymentType.equals(PaymentMethod.WECHAT.getCode())) {
            return "微信";
        }
        if (paymentType.equals(PaymentMethod.ALIPAY.getCode())) {
            return "支付宝";
        }
        if (paymentType.equals(PaymentMethod.BALANCE.getCode())) {
            return "本金余额";
        }
        if (paymentType.equals(PaymentMethod.SPECIAL_FUND.getCode())) {
            return "专用金";
        }
        if (paymentType.equals(PaymentMethod.COUPON.getCode())) {
            return "优惠券";
        }
        if (paymentType.equals(PaymentMethod.CREDIT.getCode())) {
            return "赊销";
        }
        if (paymentType.equals(PaymentMethod.MIXED.getCode())) {
            return "混合支付";
        }
        return "未知";
    }

    private static BigDecimal safeSubtract(BigDecimal total, BigDecimal used) {
        BigDecimal result = defaultAmount(total).subtract(defaultAmount(used));
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    private static BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    static final class PaymentSummary {
        final BigDecimal receivedAmount;
        final BigDecimal creditAmount;

        private PaymentSummary(BigDecimal receivedAmount, BigDecimal creditAmount) {
            this.receivedAmount = receivedAmount == null ? BigDecimal.ZERO : receivedAmount;
            this.creditAmount = creditAmount == null ? BigDecimal.ZERO : creditAmount;
        }
    }

}
