package com.agri_system.application.product.exception;

/**
 * 商品模块错误码定义
 * 
 * @author AI Generated
 */
public class ProductErrorCode {
    
    /** 商品不存在 */
    public static final String PRODUCT_NOT_FOUND = "product-1001";
    
    /** 商品编码(code)已存在 */
    public static final String PRODUCT_CODE_EXISTS = "product-1002";
    
    /** 分类不存在或已失效 */
    public static final String CATEGORY_NOT_FOUND = "product-1003";
    
    /** 分类下存在商品，无法删除 */
    public static final String CATEGORY_HAS_PRODUCTS = "product-1004";
    
    /** 单位换算配置冲突 */
    public static final String UNIT_CONVERSION_CONFLICT = "product-1005";
    
    /** 商品已上架，禁止敏感修改（如需修改需先下架） */
    public static final String PRODUCT_ONLINE_CANNOT_MODIFY = "product-1006";

    /** 图片上传失败 */
    public static final String PRODUCT_IMAGE_UPLOAD_FAILED = "product-1007";
    
    private ProductErrorCode() {
        // 工具类，禁止实例化
    }
}






