package com.agri_system.application.order.service;

import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.command.PaymentDetailDTO;
import com.agri_system.domain.customer.model.Customer;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import org.springframework.util.StringUtils;

/** POS输入合法性；只拒绝不合法输入，不定价、不核销、不记钱货或改变事务。 */
final class PosOrderInputPolicy {
    private PosOrderInputPolicy() {}

    /**
     * 履约方式与收货信息的结构校验，只看命令行本身、不查库，放在开单最前。
     *
     * <p>门店配送有两条合法来源，但不允许"两边各填一半"拼出收货信息：</p>
     * <ol>
     *   <li>三项收货信息都填齐（后台收银台历史行为）；</li>
     *   <li>三项都不填且已选客户 → 全部取自客户档案（员工端只选客户，不手抄地址）。</li>
     * </ol>
     * <p>只填一部分视为输入错误：拼出来的收货信息无法追溯来源，配送员按错误地址跑一趟的代价比拒绝开单高。</p>
     *
     * @return 履约方式：1 当场交货、2 门店配送
     */
    static int validatePosDelivery(POSOrderCreateCommand command) {
        int type = command.getDeliveryType() == null ? 1 : command.getDeliveryType();
        if (type != 1 && type != 2) throw new IllegalArgumentException("POS履约方式只支持当场交货或门店配送");
        if (type == 1) return 1;

        String name = trim(command.getReceiverName());
        String phone = trim(command.getReceiverPhone());
        String address = trim(command.getReceiverAddress());
        boolean none = name == null && phone == null && address == null;
        if (!none && (name == null || phone == null || address == null)) {
            throw new IllegalArgumentException("门店配送的收货信息需完整填写；只选客户时请把收货人、电话、地址全部留空，由客户档案带出");
        }
        return 2;
    }

    /**
     * 门店配送的三项收货信息全空时，按客户档案带出并回写命令行。
     *
     * <p>在客户行锁之后调用：客户既可能是订单外键，也可能是收货信息来源，锁一次即可判定，避免档案在开单过程中被改动。</p>
     */
    static void fillPosDeliveryFromCustomer(POSOrderCreateCommand command, Customer customer) {
        if (trim(command.getReceiverName()) != null || trim(command.getReceiverPhone()) != null
                || trim(command.getReceiverAddress()) != null) {
            return; // 收银台已手填齐全，以手填为准
        }
        if (customer == null) {
            throw new IllegalArgumentException("门店配送必须选择客户，收货人、收货电话及收货地址取自客户档案");
        }
        if (Integer.valueOf(2).equals(customer.getStatus())) {
            throw new IllegalArgumentException("该客户档案已停用，不能开配送单");
        }
        String name = trim(customer.getName());
        String phone = trim(customer.getPhone());
        String address = trim(customer.getAddress());
        if (name == null || phone == null || address == null) {
            throw new IllegalArgumentException("该客户档案缺少收货人、收货电话或收货地址，请先在后台补全客户档案，或完整填写本次收货信息");
        }
        command.setReceiverName(name);
        command.setReceiverPhone(phone);
        command.setReceiverAddress(address);
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
