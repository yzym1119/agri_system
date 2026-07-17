package com.agri_system.domain.order.enums;

/**
 * 订单主支付方式 / 下单入参支付方式
 *
 * 与 order.payment_method、POS paymentType、小程序 externalPaymentType 使用同一套编码：
 * 1-现金，2-微信，3-支付宝，4-余额，5-优惠券，6-赊销，10-混合支付
 */
public enum OrderMainPaymentMethod {

    CASH(1, "现金"),
    WECHAT(2, "微信"),
    ALIPAY(3, "支付宝"),
    BALANCE(4, "余额"),
    COUPON(5, "优惠券"),
    CREDIT(6, "赊销"),
    MIXED(10, "混合支付");

    private final int code;
    private final String desc;

    OrderMainPaymentMethod(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
