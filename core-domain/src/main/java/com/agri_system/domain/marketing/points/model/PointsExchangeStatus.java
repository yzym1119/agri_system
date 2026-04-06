package com.agri_system.domain.marketing.points.model;

import java.util.Arrays;

public enum PointsExchangeStatus {

    PENDING_REVIEW(10, "待审核"),
    PREPARING(20, "备货中"),
    WAITING_PICKUP(30, "待自提"),
    FINISHED(50, "已完成"),
    REJECTED(99, "已拒绝");

    private final int code;
    private final String desc;

    PointsExchangeStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static PointsExchangeStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code == code)
                .findFirst()
                .orElse(null);
    }
}

