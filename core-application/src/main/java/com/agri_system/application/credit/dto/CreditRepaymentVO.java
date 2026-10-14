package com.agri_system.application.credit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreditRepaymentVO {
    private Long id;
    private String repaymentNo;
    private String requestKey;
    private Long customerId;
    private Long storeId;
    private BigDecimal amount;
    private BigDecimal refundedAmount;
    private List<Allocation> allocations;
    private Integer paymentMethod;
    private String transactionId;
    private String remark;
    private LocalDateTime createdAt;

    @Data
    public static class Allocation {
        private Long id;
        private Long creditAccountId;
        private Long orderId;
        private String orderNo;
        private BigDecimal amount;
        private BigDecimal refundedAmount;
    }
}






