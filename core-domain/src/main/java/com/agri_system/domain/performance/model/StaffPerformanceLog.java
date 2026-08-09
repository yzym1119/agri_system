package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 绩效流水
 */
@Data
@Table("staff_performance_log")
public class StaffPerformanceLog {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long staffId;

    @Column(tenantId = true)
    private Long storeId;

    private Integer bizType;

    private Long bizId;

    private Long orderItemId;

    private BigDecimal amount;

    private String calcDesc;

    private Integer needReview;

    /** 1-冻结 2-已解冻 */
    private Integer status;

    /** 解冻时点:冻结类=created_at+冻结期,非冻结类=created_at */
    private LocalDateTime freezeUntil;

    private LocalDateTime createdAt;

    /** 标记为冻结(退货窗口内) */
    public void markFrozen(int freezeDays) {
        this.status = PerformanceLogStatus.FROZEN.getCode();
        this.freezeUntil = LocalDateTime.now().plusDays(freezeDays);
    }

    /** 标记为立即解冻(即算即发,旧行为) */
    public void markImmediate() {
        this.status = PerformanceLogStatus.UNFROZEN.getCode();
        this.freezeUntil = LocalDateTime.now();
    }

    public static StaffPerformanceLog negativeOf(StaffPerformanceLog positive, BigDecimal negativeAmount, String reason) {
        StaffPerformanceLog log = new StaffPerformanceLog();
        log.setStaffId(positive.getStaffId());
        log.setStoreId(positive.getStoreId());
        log.setBizType(PerformanceBizType.REFUND_DEDUCT.getCode());
        log.setBizId(positive.getBizId());
        log.setOrderItemId(positive.getOrderItemId());
        log.setAmount(negativeAmount.negate());
        log.setCalcDesc(reason);
        log.setNeedReview(0);
        return log;
    }
}

