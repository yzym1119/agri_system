package com.agri_system.domain.productpackage.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐实体（聚合根）
 * 
 * @author AI Generated
 */
@Data
@Table("product_package")
public class ProductPackage {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 套餐编码（系统生成、唯一） */
    private String code;
    
    /** 套餐名称 */
    private String name;
    
    /** 零售价（必填） */
    private BigDecimal suggestedRetailPrice;
    
    /** VIP价（可空，null=不启用VIP价） */
    private BigDecimal vipPrice;
    
    /** 最低售价 */
    private BigDecimal minPrice;
    
    /** 状态：1=上架，0=下架，-1=删除 */
    private Integer status;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /**
     * 判断是否已上架
     */
    public boolean isOnline() {
        return status != null && status == 1;
    }
    
    /**
     * 判断是否已删除
     */
    public boolean isDeleted() {
        return status != null && status == -1;
    }
}










