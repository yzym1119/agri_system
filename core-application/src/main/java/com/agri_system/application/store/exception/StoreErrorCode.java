package com.agri_system.application.store.exception;

/**
 * 门店模块错误码定义
 * 
 * @author AI Generated
 */
public class StoreErrorCode {
    
    /** 门店不存在 */
    public static final String STORE_NOT_FOUND = "store-1001";
    
    /** 门店编码已存在 */
    public static final String STORE_CODE_EXISTS = "store-1002";
    
    /** 指定的店长不存在或已离职 */
    public static final String MANAGER_NOT_FOUND = "store-1003";
    
    /** 该员工权限不足（需 MANAGER 或 ADMIN 角色） */
    public static final String MANAGER_INSUFFICIENT_PERMISSION = "store-1004";
    
    /** 门店已停用，禁止操作 */
    public static final String STORE_DISABLED = "store-1005";
    
    private StoreErrorCode() {
        // 工具类，禁止实例化
    }
}










