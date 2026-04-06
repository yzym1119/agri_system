package com.agri_system.application.inventory.exception;

/**
 * 库存模块错误码
 * 
 * @author AI Generated
 */
public class InventoryErrorCode {
    
    /** 库存不足 */
    public static final String INSUFFICIENT_STOCK = "inventory-2001";
    
    /** 商品不存在 */
    public static final String PRODUCT_NOT_FOUND = "inventory-2002";
    
    /** 批次扣减出现负数（数据异常） */
    public static final String BATCH_NEGATIVE_QUANTITY = "inventory-2003";
    
    /** 调拨目标门店不可用 */
    public static final String TARGET_STORE_UNAVAILABLE = "inventory-2004";
    
    /** 并发冲突，请稍后重试（锁失败） */
    public static final String CONCURRENT_CONFLICT = "inventory-2005";
    
    private InventoryErrorCode() {
        // 工具类，禁止实例化
    }
}

