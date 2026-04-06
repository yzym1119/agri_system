package com.agri_system.application.credit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreditRepaymentVO {
    private Long id;
    private String repaymentNo;
    private BigDecimal amount;
    private Integer paymentMethod;
    private String transactionId;
    private String remark;
    private LocalDateTime createdAt;
}






