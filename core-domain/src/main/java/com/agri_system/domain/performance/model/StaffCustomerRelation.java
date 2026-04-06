package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("staff_customer_relation")
public class StaffCustomerRelation {

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_APPROVED = 2;
    public static final int STATUS_REJECTED = 3;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private Long staffId;

    private Long customerId;

    private LocalDateTime bindTime;

    private Integer status;

    private LocalDateTime approvedAt;

    private LocalDateTime firstEffectiveAt;

    private String bindSource;

    private Long bindOrderId;

    public boolean isApproved() {
        return STATUS_APPROVED == status;
    }

    public void approve(Long orderId) {
        this.status = STATUS_APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.bindOrderId = orderId;
    }

    public void markFirstEffective() {
        this.firstEffectiveAt = LocalDateTime.now();
    }
}

