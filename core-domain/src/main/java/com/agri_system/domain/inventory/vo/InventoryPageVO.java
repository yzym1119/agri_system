package com.agri_system.domain.inventory.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存分页列表 VO
 */
@Data
public class InventoryPageVO {

    private Long productId;

    private String productName;
    
    private String specification;
    
    private String manufacturer;

    private Long storeId;

    private String storeName;

    private BigDecimal totalQuantity;

    /** 当前可售精确展示量；可换算时主单位，否则明确用基准单位，不舍入。 */
    private String saleableQuantity;
    private String saleableUnit;
    private String availableBaseQuantity;

    private String unit;
    
    private String mainUnit;
    
    private String auxUnit;
    
    private BigDecimal mainToAuxRatio;
}


