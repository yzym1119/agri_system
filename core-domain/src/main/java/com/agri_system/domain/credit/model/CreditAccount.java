package com.agri_system.domain.credit.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Table("credit_account")
public class CreditAccount {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long customerId;
    private Long orderId;
    private BigDecimal creditAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private LocalDate dueDate;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isCleared() {
        return remainingAmount != null && remainingAmount.compareTo(BigDecimal.ZERO) <= 0;
    }

    public BigDecimal applyRepayment(BigDecimal repayAmount) {
        if (repayAmount == null || repayAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("还款金额必须大于0");
        }
        BigDecimal remaining = remainingAmount == null ? BigDecimal.ZERO : remainingAmount;
        BigDecimal paid = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        BigDecimal actual = repayAmount.min(remaining);

        this.paidAmount = paid.add(actual);
        this.remainingAmount = remaining.subtract(actual);
        if (this.remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            this.remainingAmount = BigDecimal.ZERO;
            this.status = 2;
        } else {
            this.status = 1;
        }
        return actual;
    }
}






