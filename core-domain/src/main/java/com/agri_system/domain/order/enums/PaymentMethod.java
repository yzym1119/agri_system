package com.agri_system.domain.order.enums;

/**
 * 支付记录类型枚举
 *
 * 对应 order_payment.payment_type：
 * 1-现金，2-微信，3-支付宝，4-本金余额，5-优惠券，6-赊销，8-专用金
 */
public enum PaymentMethod {

    CASH(1, "现金"),
    WECHAT(2, "微信"),
    ALIPAY(3, "支付宝"),
    BALANCE(4, "本金余额"),
    COUPON(5, "红包/优惠券"),
    CREDIT(6, "赊销"),
    SPECIAL_FUND(8, "专用金余额"),
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

