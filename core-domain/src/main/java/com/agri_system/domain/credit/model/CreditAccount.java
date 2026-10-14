package com.agri_system.domain.credit.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Column;
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
    @Column(tenantId = true)
    private Long storeId;
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
        refreshSettlementStatus();
        return actual;
    }

    /** 还款与退货冲欠款共用状态规则；缺失历史到期日不猜测为未逾期。 */
    public void refreshSettlementStatus() {
        if (remainingAmount == null || remainingAmount.signum() < 0) throw new IllegalArgumentException("应收余额不能为空或为负数");
        if (remainingAmount.signum() == 0) {
            status = 2;
        } else {
            boolean overdue = dueDate == null ? Integer.valueOf(3).equals(status) : dueDate.isBefore(LocalDate.now());
            status = overdue ? 3 : 1;
        }
    }
}






