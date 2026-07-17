package com.agri_system.domain.product.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（聚合根）
 * 
 * @author AI Generated
 */
@Data
@Table("product")
public class Product {
    
    /** 主键ID */
    @Id(keyType = KeyType.Auto)
    private Long id;
    
    /** 商品编码 */
    private String code;

    /** 条形码（EAN-13/Code128） */
    private String barcode;

    /** 商品名称 */
    private String name;
    
    /** 商品分类ID */
    private Long categoryId;
    
    /** 规格（如：500g/袋） */
    private String specification;
    
    /** 剂型（如：颗粒剂、水剂、粉剂） */
    private String dosageForm;
    
    /** 生产厂家 */
    private String manufacturer;

    /** 农药/肥料登记证号 */
    private String registrationNo;
    
    /** 主单位（如：袋、瓶、箱） */
    private String mainUnit;
    
    /** 辅单位（如：箱、件） */
    private String auxUnit;
    
    /** 主辅单位换算比例 */
    private BigDecimal mainToAuxRatio;
    
    /** 建议零售价（元） */
    private BigDecimal suggestedRetailPrice;
    
    /** VIP价格（元） */
    private BigDecimal vipPrice;
    
    /** 最低限价（元） */
    private BigDecimal minPrice;
    
    /** 移动加权平均成本（元，只读，由采购模块维护） */
    private BigDecimal avgCostPrice;
    
    /** 主图URL */
    private String imageUrl;
    
    /** 详情文字（纯文字详情） */
    private String detailText;
    
    /** 是否上架：1-上架，0-下架 */
    private Integer isOnline;
    
    /** 线上价（元） */
    private BigDecimal onlinePrice;
    
    /** 基础运费（元） */
    private BigDecimal baseShippingFee;
    
    /** 允许专用金：1-允许，0-不允许 */
    private Integer allowSpecialFund;
    
    /** 状态：1-正常，2-停用 */
    private Integer status;

    /** 逻辑删除：1-已删，0-未删 */
    @Column(isLogicDelete = true)
    private Integer deleted;

    /** 备注 */
    private String remark;

    /** 存储条件（如：阴凉干燥处、避光保存） */
    private String storageConditions;

    /** 保质期（天） */
    private Integer shelfLifeDays;

    /** 使用方法/稀释比例 */
    private String usageMethod;

    /** 最低库存预警值 */
    private BigDecimal minStock;

    /** 最高库存预警值 */
    private BigDecimal maxStock;

    /** 建议补货数量 */
    private BigDecimal reorderQuantity;

    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** 更新时间 */
    private LocalDateTime updatedAt;
    
    /** 创建人ID */
    private Long createdBy;
    
    /** 更新人ID */
    private Long updatedBy;
    
    /**
     * 检查商品是否已上架
     */
    public boolean isOnline() {
        return isOnline != null && isOnline == 1;
    }
    
    /**
     * 检查商品是否可修改敏感字段
     * 已上架的商品禁止修改某些敏感字段
     */
    public boolean canModifySensitiveFields() {
        return !isOnline();
    }
    
    /**
     * 上架商品
     */
    public void online() {
        this.isOnline = 1;
    }
    
    /**
     * 下架商品
     */
    public void offline() {
        this.isOnline = 0;
    }
}

