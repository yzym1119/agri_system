package com.agri_system.domain.after_sales.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后聚合根
 *
 * @author AI
 */
@Data
@Table("after_sales")
public class AfterSales {

    public static final int TYPE_CLIENT_CANCEL = 1;
    public static final int TYPE_ADMIN_RETURN = 2;

    public static final int STATUS_APPLIED = 10;
    public static final int STATUS_WAIT_REFUND = 40;
    public static final int STATUS_FINISHED = 50;
    public static final int STATUS_REJECTED = 90;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String afterSalesNo;

    /** 正式退款确认的报价身份，历史未知保持NULL。 */
    private String refundQuoteId;
    /** 原请求及实际员工的完整规范化摘要，用于重放核对。 */
    private String refundRequestHash;

    private Long orderId;

    private Long customerId;

    @Column(tenantId = true)
    private Long storeId;

    private Integer type;

    private Integer status;

    private BigDecimal refundAmount;
    /** 退货成交冲销中实际冲减原单欠款的金额，历史未知为NULL。 */
    private BigDecimal creditReductionAmount;
    /** 退货成交冲销中退回已收款的金额（包括钱包），历史未知为NULL。 */
    private BigDecimal cashRefundAmount;

    private String reason;

    private String auditRemark;

    private Long auditBy;

    private LocalDateTime auditTime;

    private Long refundBy;

    private LocalDateTime refundTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isPending() {
        return STATUS_APPLIED == status;
    }

    public boolean isWaitRefund() {
        return STATUS_WAIT_REFUND == status;
    }

    public void markWaitRefund(Long auditor, String remark) {
        this.status = STATUS_WAIT_REFUND;
        this.auditBy = auditor;
        this.auditRemark = remark;
        this.auditTime = LocalDateTime.now();
    }

    public void markRejected(Long auditor, String remark) {
        this.status = STATUS_REJECTED;
        this.auditBy = auditor;
        this.auditRemark = remark;
        this.auditTime = LocalDateTime.now();
    }

    public void markFinished(Long operator) {
        this.status = STATUS_FINISHED;
        this.refundBy = operator;
        this.refundTime = LocalDateTime.now();
    }
}








