package com.agri_system.application.purchase.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采购单分页查询条件
 *
 * @author AI Generated
 */
@Data
@Schema(description = "采购单分页查询条件")
public class PurchasePageQuery {

    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "门店ID（可选）", example = "1")
    private Long storeId;

    @Schema(description = "供应商名称（模糊查询，可选）", example = "张三农资")
    private String supplierName;

    @Schema(description = "采购单号（模糊查询，可选）", example = "PO2024")
    private String orderNo;

    @Schema(description = "状态：1-待提交，2-待入库，3-已入库，4-已取消", example = "2")
    private Integer status;

    @Schema(description = "创建时间开始", example = "2024-01-01T00:00:00")
    private LocalDateTime createdStartTime;

    @Schema(description = "创建时间结束", example = "2024-12-31T23:59:59")
    private LocalDateTime createdEndTime;
}










