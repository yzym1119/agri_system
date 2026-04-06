package com.agri_system.domain.credit.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Table("credit_statement")
public class CreditStatement {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long customerId;
    private String statementNo;
    private LocalDate statementDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalCreditAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal totalRemainingAmount;
    private Integer status;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
    private String remark;
    private LocalDateTime createdAt;
    private Long createdBy;
}






