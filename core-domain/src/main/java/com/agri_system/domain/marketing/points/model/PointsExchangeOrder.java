package com.agri_system.domain.marketing.points.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("points_exchange_order")
public class PointsExchangeOrder {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String exchangeNo;
    private Long customerId;
    private Long pointsGoodsId;
    private Integer points;
    private Integer quantity;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Integer status;
    private String pickupCode;
    private String logisticsCompany;
    private String trackingNo;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void markAuditPassed() {
        ensureStatus(PointsExchangeStatus.PENDING_REVIEW);
        this.status = PointsExchangeStatus.PREPARING.getCode();
    }

    public void markAuditRejected() {
        ensureStatus(PointsExchangeStatus.PENDING_REVIEW);
        this.status = PointsExchangeStatus.REJECTED.getCode();
    }

    public void markNotify() {
        ensureStatus(PointsExchangeStatus.PREPARING);
        this.status = PointsExchangeStatus.WAITING_PICKUP.getCode();
    }

    public void markFinished() {
        ensureStatus(PointsExchangeStatus.WAITING_PICKUP);
        this.status = PointsExchangeStatus.FINISHED.getCode();
    }

    public PointsExchangeStatus status() {
        return PointsExchangeStatus.fromCode(status);
    }

    private void ensureStatus(PointsExchangeStatus expected) {
        if (expected == null) {
            throw new IllegalArgumentException("期望的状态不能为空");
        }
        PointsExchangeStatus current = status();
        if (current != expected) {
            throw new IllegalStateException("状态不允许的流转，当前: " + current + "，期望: " + expected);
        }
    }
}

