package com.agri_system.application.order.service;

import com.agri_system.domain.order.model.OrderItem;
import com.agri_system.domain.order.model.Order;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 所有物料按原净成交份额与累计实退数量限制退款，末次退货结清分钱尾差。 */
public final class RefundAmountPolicy {
    private RefundAmountPolicy() {}

    /** 未提供净额分摊时使用原成交小计，实际订单入口使用带净额的重载。 */
    public static BigDecimal maximumForReturn(OrderItem item, BigDecimal alreadyReturnedQuantity,
                                              BigDecimal alreadyRefundedAmount, BigDecimal requestedQuantity) {
        return maximumForReturn(item, alreadyReturnedQuantity, alreadyRefundedAmount, requestedQuantity,
                grossLineTotal(item));
    }

    /** 按原应结算额分配净份额，成交快照仍保留原小计。 */
    public static BigDecimal maximumForReturn(OrderItem item, BigDecimal alreadyReturnedQuantity,
                                              BigDecimal alreadyRefundedAmount, BigDecimal requestedQuantity,
                                              BigDecimal netLineTotal) {
        if (item == null || item.getQuantity() == null || item.getQuantity().signum() <= 0
                || requestedQuantity == null || requestedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("退货数量必须大于0且有原销售数量");
        }
        if (requestedQuantity.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("退货数量最多支持4位有效小数");
        }
        BigDecimal cumulativeQuantity = amount(alreadyReturnedQuantity).add(requestedQuantity);
        if (cumulativeQuantity.compareTo(item.getQuantity()) > 0) {
            throw new IllegalArgumentException("累计退货数量不能超过原购买数量");
        }
        BigDecimal total = netLineTotal;
        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException("订单物料缺少有效可退款金额");
        }
        {
            BigDecimal cumulativeLimit = cumulativeQuantity.compareTo(item.getQuantity()) == 0
                    ? total : total.multiply(cumulativeQuantity).divide(item.getQuantity(), 2, RoundingMode.HALF_UP);
            return cumulativeLimit.subtract(amount(alreadyRefundedAmount)).max(BigDecimal.ZERO);
        }
    }

    /**
     * 所有商品把整单优惠和优惠券按原成交小计分到物料，按分守恒。
     * 人工少退仍可在净份额内执行；超过商品小计的实付部分不提高物料退款额度。
     */
    public static Map<Long, BigDecimal> refundableBaseAmounts(Order order, List<OrderItem> items) {
        Map<Long, BigDecimal> gross = new LinkedHashMap<>();
        for (OrderItem item : items) {
            if (item.getId() == null || gross.putIfAbsent(item.getId(), grossLineTotal(item)) != null) {
                throw new IllegalArgumentException("订单明细必须有唯一ID");
            }
        }
        BigDecimal grossTotal = gross.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = amount(order.getPayAmount());
        if (paid.signum() < 0) throw new IllegalArgumentException("原订单实付金额无效");
        if (grossTotal.signum() == 0) return gross;
        BigInteger denominator = cents(grossTotal);
        BigInteger cash = cents(paid.min(grossTotal));
        Map<Long, BigInteger> allocated = new LinkedHashMap<>();
        List<Map.Entry<Long, BigInteger>> remainders = new ArrayList<>();
        BigInteger assigned = BigInteger.ZERO;
        for (Map.Entry<Long, BigDecimal> line : gross.entrySet()) {
            BigInteger[] share = cash.multiply(cents(line.getValue())).divideAndRemainder(denominator);
            allocated.put(line.getKey(), share[0]); assigned = assigned.add(share[0]);
            remainders.add(Map.entry(line.getKey(), share[1]));
        }
        remainders.sort(Map.Entry.<Long, BigInteger>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()));
        int extra = cash.subtract(assigned).intValueExact();
        for (int index = 0; index < extra; index++) {
            allocated.merge(remainders.get(index).getKey(), BigInteger.ONE, BigInteger::add);
        }
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        allocated.forEach((id, value) -> result.put(id, new BigDecimal(value, 2)));
        return result;
    }

    private static BigDecimal grossLineTotal(OrderItem item) {
        if (item == null) throw new IllegalArgumentException("订单物料不能为空");
        BigDecimal total = item.getTotalPrice();
        if (item.getPackageId() != null && (total == null || total.signum() < 0)) {
            throw new IllegalArgumentException("套餐物料缺少有效成交小计快照");
        }
        if (item.getPackageId() == null && (total == null || total.signum() <= 0)) {
            total = item.getUnitPrice() == null || item.getQuantity() == null
                    ? BigDecimal.ZERO : item.getUnitPrice().multiply(item.getQuantity()).setScale(2, RoundingMode.HALF_UP);
        }
        if (total == null || total.signum() < 0) throw new IllegalArgumentException("原成交小计无效");
        return total;
    }

    private static BigInteger cents(BigDecimal amount) {
        try { return amount.setScale(2, RoundingMode.UNNECESSARY).unscaledValue(); }
        catch (ArithmeticException invalid) { throw new IllegalArgumentException("原成交金额必须精确到分", invalid); }
    }

    /** 原订单实付金额不会因部分退款改变，因此每次都与累计已退金额一起校验。 */
    public static void validateOrderRefund(BigDecimal paidAmount, BigDecimal alreadyRefundedAmount,
                                           BigDecimal requestedAmount) {
        if (requestedAmount == null || requestedAmount.signum() < 0 || requestedAmount.scale() > 2) {
            throw new IllegalArgumentException("退款金额不能为负且最多两位小数");
        }
        if (amount(alreadyRefundedAmount).add(requestedAmount).compareTo(amount(paidAmount)) > 0) {
            throw new IllegalArgumentException("累计退款金额不能超过订单原实付金额");
        }
    }

    private static BigDecimal amount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
