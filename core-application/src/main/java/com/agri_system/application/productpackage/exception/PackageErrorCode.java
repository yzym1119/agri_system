package com.agri_system.application.productpackage.exception;

/**
 * 套餐模块错误码定义
 * 
 * @author AI Generated
 */
public class PackageErrorCode {
    
    /** 套餐不存在 */
    public static final String PACKAGE_NOT_FOUND = "package-1001";
    
    /** 套餐编码重复 */
    public static final String PACKAGE_CODE_EXISTS = "package-1002";
    
    /** 套餐必须至少包含一个商品 */
    public static final String PACKAGE_MUST_HAVE_ITEMS = "package-1004";
    
    /** 套餐包含重复商品 */
    public static final String PACKAGE_DUPLICATE_PRODUCTS = "package-1005";
    
    private PackageErrorCode() {
        // 工具类，禁止实例化
    }
}










