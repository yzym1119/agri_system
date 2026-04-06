package com.agri_system.domain.product.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品单位换算实体
 * 
 * @author AI Generated
 */
@Data
@Table("product_unit_conversion")
public class ProductUnitConversion {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 商品ID */
    private Long productId;
    
    /** 源单位 */
    private String fromUnit;
    
    /** 目标单位 */
    private String toUnit;
    
    /** 换算比例（1源单位=？目标单位） */
    private BigDecimal conversionRatio;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 检查单位换算是否冲突
     * 同一个商品ID下，from_unit 和 to_unit 组合必须唯一
     */
    public String getUniqueKey() {
        return productId + "_" + fromUnit + "_" + toUnit;
    }
}

