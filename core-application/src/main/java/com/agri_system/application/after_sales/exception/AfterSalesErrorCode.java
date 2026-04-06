package com.agri_system.application.after_sales.exception;

/**
 * 售后错误码
 */
public enum AfterSalesErrorCode {
    ORDER_STATUS_INVALID("after_sales-1001"),
    ORDER_NOT_FOUND("after_sales-1002"),
    AFTER_SALES_EXISTS("after_sales-1003"),
    AFTER_SALES_NOT_FOUND("after_sales-1004"),
    AFTER_SALES_STATUS_INVALID("after_sales-1005"),
    CREDIT_ORDER_NOT_SUPPORTED("after_sales-1006");

    private final String code;

    AfterSalesErrorCode(String code) {
        this.code = code;
    }

    @Override
    public String toString() {
        return code;
    }
}








