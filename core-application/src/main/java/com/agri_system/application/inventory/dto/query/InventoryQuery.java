package com.agri_system.application.inventory.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 库存分页查询条件
 */
@Data
@Schema(description = "库存分页查询条件")
public class InventoryQuery {

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "门店ID（可选）", example = "1")
    private Long storeId;

    @Schema(description = "商品名称（模糊查询，可选）", example = "复合肥料")
    private String productName;

    @Schema(description = "是否仅显示有库存记录", example = "true")
    private Boolean onlyHasStock;
}

