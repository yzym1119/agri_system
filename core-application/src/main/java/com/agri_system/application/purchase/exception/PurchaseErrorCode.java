package com.agri_system.application.purchase.exception;

/**
 * 采购模块错误码定义
 * 
 * @author AI Generated
 */
public class PurchaseErrorCode {
    
    /** 采购单不存在 */
    public static final String PURCHASE_ORDER_NOT_FOUND = "purchase-3001";
    
    /** 采购单状态不允许当前操作 */
    public static final String PURCHASE_ORDER_STATUS_INVALID = "purchase-3002";
    
    /** 采购单已入库，禁止重复入库 */
    public static final String PURCHASE_ORDER_ALREADY_INBOUND = "purchase-3003";
    
    /** 商品或门店不存在 */
    public static final String PRODUCT_OR_STORE_NOT_FOUND = "purchase-3004";
    
    /** 数量或价格不合法 */
    public static final String INVALID_QTY_OR_PRICE = "purchase-3005";
    
    private PurchaseErrorCode() {
        // 工具类，禁止实例化
    }
}








