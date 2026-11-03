package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.command.PaymentDetailDTO;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import org.springframework.util.StringUtils;

/** POS输入合法性；只拒绝不合法输入，不定价、不核销、不记钱货或改变事务。 */
final class PosOrderInputPolicy {
    private PosOrderInputPolicy() {}
    static int validatePosDelivery(POSOrderCreateCommand command) {
        int type = command.getDeliveryType() == null ? 1 : command.getDeliveryType();
        if (type != 1 && type != 2) throw new IllegalArgumentException("POS履约方式只支持当场交货或门店配送");
        if (type == 2 && (!StringUtils.hasText(command.getReceiverName()) || !StringUtils.hasText(command.getReceiverPhone())
                || !StringUtils.hasText(command.getReceiverAddress()))) {
            throw new IllegalArgumentException("门店配送必须填写收货人、收货电话及收货地址");
        }
        return type;
    }

    static void validatePosPayments(POSOrderCreateCommand command) {
        java.util.Set<Integer> mainTypes = java.util.Set.of(1, 2, 3, 4, 6, 10);
        if (command.getPaymentType() == null || !mainTypes.contains(command.getPaymentType())) {
            throw new IllegalArgumentException("POS支付方式不正确");
        }
        if (command.getPaymentType() == OrderMainPaymentMethod.BALANCE.getCode() && command.getCustomerId() == null) {
            throw new IllegalArgumentException("余额支付必须指定客户");
        }
        if (command.getPayments() == null || command.getPayments().isEmpty()) {
            if (command.getPaymentType() == OrderMainPaymentMethod.MIXED.getCode()) {
                throw new IllegalArgumentException("混合支付必须提供支付明细");
            }
            return;
        }
        java.util.Set<Integer> tenderTypes = java.util.Set.of(1, 2, 3, 4, 6);
        for (PaymentDetailDTO detail : command.getPayments()) {
            if (detail == null || detail.getPaymentType() == null || !tenderTypes.contains(detail.getPaymentType())) {
                throw new IllegalArgumentException("支付明细方式不正确");
            }
            if (detail.getAmount() == null || detail.getAmount().signum() <= 0
                    || detail.getAmount().stripTrailingZeros().scale() > 2) {
                throw new IllegalArgumentException("每笔支付明细金额必须大于0且精确到分");
            }
        }
    }
}
