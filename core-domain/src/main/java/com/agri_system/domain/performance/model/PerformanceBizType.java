package com.agri_system.domain.performance.model;

/**
 * 绩效业务类型
 */
public enum PerformanceBizType {

    SALES_COMMISSION(1),
    RECHARGE_GUIDE_REWARD(2),
    NEW_CUSTOMER_REWARD(3),
    REFUND_DEDUCT(4),
    ADJUSTMENT(5),
    CUSTOMER_MAINTAIN_REWARD(6),
    PACKAGE_PROMOTION(7),
    BRAND_PROMOTION_REWARD(8);

    private final int code;

    PerformanceBizType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PerformanceBizType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (PerformanceBizType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}

