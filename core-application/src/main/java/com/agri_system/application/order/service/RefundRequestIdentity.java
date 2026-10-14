package com.agri_system.application.order.service;

import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.order.dto.command.OrderRefundCommand;
import com.agri_system.domain.after_sales.model.AfterSales;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** 退款重试核对原请求；不从退款后的剩余金额或数量重建请求。 */
public final class RefundRequestIdentity {
    private RefundRequestIdentity() {}

    public static String requireQuote(String quoteId) {
        if (quoteId == null || quoteId.isBlank()) throw new IllegalArgumentException("请先确认最新退货资金预览");
        return quoteId;
    }

    public static String hash(OrderRefundCommand command, Long actor) {
        return hash("ORDER_REFUND", command.getQuoteId(), command.getOrderId(), actor,
                command.getRefundReason(), command.getRefundAmount(),
                command.getItems() == null ? null : command.getItems().stream()
                        .map(i -> new Line(i.getOrderItemId(), i.getQuantity(), i.getRefundAmount())).toList());
    }

    public static String hash(AdminReturnCommand command, Long actor) {
        return hash("ADMIN_RETURN", command.getQuoteId(), command.getOrderId(), actor,
                command.getReason(), command.getRefundAmount(),
                command.getItems() == null ? null : command.getItems().stream()
                        .map(i -> new Line(i.getOrderItemId(), i.getQuantity(), i.getRefundAmount())).toList());
    }

    public static String hash(AfterSalesRefundCommand command, Long orderId, Long actor) {
        return hash("AFTER_SALES_REFUND:" + command.getAfterSalesId(), command.getQuoteId(), orderId, actor,
                null, command.getRefundAmount(), null);
    }

    /** 同一身份只允许确认同一已完成结果，未完成状态不能当作成功。 */
    public static void validateCompleted(AfterSales fact, Long orderId, Long actor, String quoteId, String requestHash) {
        if (!Objects.equals(fact.getOrderId(), orderId) || !Objects.equals(fact.getRefundBy(), actor)
                || !Objects.equals(fact.getRefundQuoteId(), quoteId)
                || !Objects.equals(fact.getRefundRequestHash(), requestHash)) {
            throw new IllegalArgumentException("相同退货预览不能用于不同请求");
        }
        if (!Integer.valueOf(AfterSales.STATUS_FINISHED).equals(fact.getStatus())) {
            throw new IllegalArgumentException("原退款请求尚未完成，请核对原售后单");
        }
    }

    private static String hash(String entry, String quoteId, Long orderId, Long actor, String reason,
                               BigDecimal amount, List<Line> lines) {
        StringBuilder data = new StringBuilder();
        value(data, entry); value(data, quoteId); value(data, orderId); value(data, actor); value(data, reason);
        value(data, decimal(amount));
        if (lines == null) value(data, null);
        else {
            value(data, lines.size());
            lines.stream().sorted(Comparator.comparing(Line::id, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(l -> decimal(l.quantity()), Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(l -> decimal(l.amount()), Comparator.nullsFirst(Comparator.naturalOrder())))
                    .forEach(line -> { value(data, line.id()); value(data, decimal(line.quantity())); value(data, decimal(line.amount())); });
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String decimal(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    private static void value(StringBuilder data, Object value) {
        if (value == null) data.append("-1:");
        else { String text = value.toString(); data.append(text.length()).append(':').append(text); }
    }
    private record Line(Long id, BigDecimal quantity, BigDecimal amount) {}
}
