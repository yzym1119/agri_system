package com.agri_system.domain.performance.model;

/** 提成流水状态:1-冻结(退货窗口内) 2-已解冻(可发放) */
public enum PerformanceLogStatus {
    FROZEN(1),
    UNFROZEN(2);

    private final int code;

    PerformanceLogStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static PerformanceLogStatus fromCode(int code) {
        for (PerformanceLogStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知提成状态码: " + code);
    }
}
