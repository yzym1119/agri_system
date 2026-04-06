package com.agri_system.application.inventory.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "库存流水VO")
public class InventoryTransactionVO {
    
    @Schema(description = "流水ID")
    private Long id;
    
    @Schema(description = "门店ID")
    private Long storeId;
    
    @Schema(description = "门店名称")
    private String storeName;
    
    @Schema(description = "商品ID")
    private Long productId;
    
    @Schema(description = "商品名称")
    private String productName;
    
    @Schema(description = "商品规格")
    private String productSpec;
    
    @Schema(description = "厂家")
    private String manufacturer;
    
    @Schema(description = "变动数量（正数为增加，负数为减少）")
    private BigDecimal quantity;
    
    @Schema(description = "结存数量（操作后库存）")
    private BigDecimal afterQuantity;
    
    @Schema(description = "流水类型代码")
    private Integer type;
    
    @Schema(description = "流水类型名称")
    private String typeName;
    
    @Schema(description = "关联单号")
    private String referenceNo;
    
    @Schema(description = "操作人ID")
    private Long createdBy;
    
    @Schema(description = "操作人姓名")
    private String createdByName;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "备注")
    private String remark;
    
    @Schema(description = "主单位")
    private String mainUnit;
    
    @Schema(description = "辅单位")
    private String auxUnit;
    
    @Schema(description = "主辅换算比例")
    private BigDecimal mainToAuxRatio;
}

