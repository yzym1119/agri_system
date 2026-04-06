package com.agri_system.application.inventory.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 库存流水查询条件
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "库存流水查询条件")
public class InventoryTransactionQuery {
    
    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;
    
    @Schema(description = "门店ID（可选）", example = "1")
    private Long storeId;
    
    @Schema(description = "商品名称（模糊查询，可选）", example = "复合肥料")
    private String productName;
    
    @Schema(description = "流水类型（可选）", example = "1")
    private Integer type;
    
    @Schema(description = "开始时间（可选）", example = "2024-01-01T00:00:00")
    private String startTime;
    
    @Schema(description = "结束时间（可选）", example = "2024-12-31T23:59:59")
    private String endTime;
}









