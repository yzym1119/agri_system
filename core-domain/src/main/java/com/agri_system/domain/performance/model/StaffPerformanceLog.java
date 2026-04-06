package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
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

    private Long storeId;

    private Integer bizType;

    private Long bizId;

    private Long orderItemId;

    private BigDecimal amount;

    private String calcDesc;

    private Integer needReview;

    private LocalDateTime createdAt;

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

