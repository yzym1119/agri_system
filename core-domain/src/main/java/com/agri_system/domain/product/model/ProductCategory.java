package com.agri_system.domain.product.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品分类实体
 * 
 * @author AI Generated
 */
@Data
@Table("product_category")
public class ProductCategory {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 分类编码 */
    private String code;
    
    /** 分类名称 */
    private String name;
    
    /** 父分类ID（为空表示顶级分类） */
    private Long parentId;
    
    /** 分类层级（1-一级，2-二级，以此类推） */
    private Integer level;
    
    /** 排序顺序 */
    private Integer sortOrder;
    
    /** 状态：1-正常，2-停用 */
    private Integer status;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /** 创建人ID */
    private Long createdBy;
    
    /** 更新人ID */
    private Long updatedBy;
    
    /**
     * 检查分类是否有效
     */
    public boolean isActive() {
        return status != null && status == 1;
    }
    
    /**
     * 检查是否为顶级分类
     */
    public boolean isRoot() {
        return parentId == null || parentId == 0;
    }
}

