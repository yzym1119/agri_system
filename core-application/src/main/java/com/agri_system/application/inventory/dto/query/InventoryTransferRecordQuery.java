package com.agri_system.application.inventory.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 调拨记录查询条件
 */
@Data
@Schema(description = "调拨记录查询条件")
public class InventoryTransferRecordQuery {

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "调出门店ID", example = "1")
    private Long fromStoreId;

    @Schema(description = "调入门店ID", example = "2")
    private Long toStoreId;

    @Schema(description = "商品名称（模糊查询）", example = "复合肥")
    private String productName;

    @Schema(description = "开始时间", example = "2026-04-01T00:00:00")
    private String startTime;

    @Schema(description = "结束时间", example = "2026-04-12T23:59:59")
    private String endTime;
}
