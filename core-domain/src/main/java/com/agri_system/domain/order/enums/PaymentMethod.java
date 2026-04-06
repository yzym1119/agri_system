package com.agri_system.domain.order.enums;

/**
 * 支付方式枚举 - 单一可信来源
 *
 * 注意：项目历史数据与多处代码使用数字常量表示支付方式，统一在此维护并通过 code 获取。
 */
public enum PaymentMethod {

    CASH(1, "现金"),
    WECHAT(2, "微信"),
    ALIPAY(3, "支付宝"),
    BALANCE(3, "本金余额"),
    SPECIAL_FUND(4, "专用金余额"),
    COUPON(5, "红包/优惠券"),
    CREDIT(6, "赊销"),
    MIXED(10, "混合支付");

    private final int code;
    private final String desc;

    PaymentMethod(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static PaymentMethod fromCode(Integer code) {
        if (code == null) return null;
        for (PaymentMethod p : values()) {
            if (p.code == code) return p;
        }
        return null;
    }
}


