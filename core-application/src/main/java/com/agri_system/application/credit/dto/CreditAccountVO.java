package com.agri_system.application.credit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CreditAccountVO {
    private Long id;
    private Long orderId;
    private String orderNo;
    private BigDecimal creditAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private LocalDate dueDate;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
}






