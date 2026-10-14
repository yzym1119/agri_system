package com.agri_system.domain.credit.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Table("credit_repayment_log")
public class CreditRepaymentLog {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String repaymentNo;
    private Long creditAccountId;
    private String requestKey;
    /** NULL 表示旧来源尚未核对，不能当作0放开退款。 */
    private BigDecimal refundedAmount;
    private Long customerId;
    @Column(tenantId = true)
    private Long storeId;
    private BigDecimal amount;
    private Integer paymentMethod;
    private String transactionId;
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt;
}






