package com.agri_system.application.credit.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreditDebtVO {
    private Long storeId;
    private BigDecimal totalDebt;
    private BigDecimal creditLimit;
    private BigDecimal remainingCredit;
    private LocalDateTime latestDebtDate;
}






