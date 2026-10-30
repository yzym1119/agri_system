package com.agri_system.application.purchase.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 采购明细VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "采购明细")
public class PurchaseItemVO {
    
    @Schema(description = "明细ID", example = "1")
    private Long id;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "商品编码", example = "P001")
    private String productCode;

    @Schema(description = "商品条码，仅定位商品，不作为批次证据")
    private String productBarcode;
    
    @Schema(description = "商品名称", example = "XX农药")
    private String productName;
    
    @Schema(description = "规格", example = "500g/袋")
    private String specification;
    
    @Schema(description = "生产厂家", example = "XX农资有限公司")
    private String manufacturer;
    
    @Schema(description = "数量", example = "100.0000")
    private BigDecimal qty;
    
    @Schema(description = "单位", example = "kg")
    private String unit;
    
    @Schema(description = "单价（元）", example = "10.50")
    private BigDecimal price;
    
    @Schema(description = "金额（元）", example = "1050.00")
    private BigDecimal amount;
}

