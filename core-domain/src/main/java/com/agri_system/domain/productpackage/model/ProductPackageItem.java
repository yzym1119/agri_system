package com.agri_system.domain.productpackage.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐明细实体
 * 
 * @author AI Generated
 */
@Data
@Table("product_package_item")
public class ProductPackageItem {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 套餐ID */
    private Long packageId;
    
    /** 商品ID */
    private Long productId;
    
    /** 数量（必须 > 0） */
    private BigDecimal quantity;
    
    /** 单位 */
    private String unit;
    
    /** 排序顺序 */
    private Integer sortOrder;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
}










