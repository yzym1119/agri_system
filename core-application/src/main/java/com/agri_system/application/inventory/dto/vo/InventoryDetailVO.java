package com.agri_system.application.inventory.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存详情VO（包含批次列表）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "库存详情")
public class InventoryDetailVO {
    
    @Schema(description = "库存快照ID", example = "1")
    private Long id;
    
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "总库存数量（快照）", example = "500.0000")
    private BigDecimal quantity;
    
    @Schema(description = "门店名称")
    private String storeName;
    
    @Schema(description = "商品名称")
    private String productName;
    
    @Schema(description = "单位")
    private String unit;
    
    @Schema(description = "批次列表")
    private List<BatchDetailVO> batches;
}









