package com.agri_system.application.order.exception;

/**
 * 订单错误码
 * 
 * @author AI Generated
 */
public class OrderErrorCode {
    
    // 基础错误码
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_STATUS_INVALID = "ORDER_STATUS_INVALID";
    public static final String ORDER_AMOUNT_INVALID = "ORDER_AMOUNT_INVALID";
    public static final String ORDER_OPERATION_NOT_SUPPORTED = "ORDER_OPERATION_NOT_SUPPORTED";
    
    // 支付相关
    public static final String PAYMENT_AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";
    public static final String WALLET_AUTH_CODE_INVALID = "WALLET_AUTH_CODE_INVALID";
    
    // 优惠券相关
    public static final String COUPON_INVALID = "COUPON_INVALID";
    
    // 自提相关
    public static final String PICKUP_CODE_NOT_FOUND = "PICKUP_CODE_NOT_FOUND";
    public static final String PICKUP_CODE_INVALID = "PICKUP_CODE_INVALID";
    
    // 赊销风控错误码
    public static final String CREDIT_NO_COUPON = "CREDIT_NO_COUPON";
    public static final String CREDIT_NO_MIXED = "CREDIT_NO_MIXED";
    public static final String CREDIT_ORDER_NO_REFUND = "CREDIT_ORDER_NO_REFUND";
    public static final String CREDIT_AMOUNT_MISMATCH = "CREDIT_AMOUNT_MISMATCH";
}
