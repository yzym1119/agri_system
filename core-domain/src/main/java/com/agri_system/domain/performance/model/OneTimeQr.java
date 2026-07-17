package com.agri_system.domain.performance.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("one_time_qr")
public class OneTimeQr {

    public static final int STATUS_CREATED = 0;
    public static final int STATUS_BOUND = 1;
    public static final int STATUS_USED = 2;
    public static final int STATUS_EXPIRED = 3;

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String qrToken;

    private Long staffId;

    @Column(tenantId = true)
    private Long storeId;

    private String orderTempToken;

    private String sceneData;

    private Integer status;

    private Long customerId;

    private String openid;

    private LocalDateTime createdAt;

    private LocalDateTime bindAt;

    private LocalDateTime expireAt;

    public boolean isExpired() {
        return expireAt != null && expireAt.isBefore(LocalDateTime.now());
    }
}

