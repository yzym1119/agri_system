package com.agri_system.application.inventory.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调拨记录VO
 */
@Data
@Schema(description = "调拨记录VO")
public class InventoryTransferRecordVO {

    @Schema(description = "调拨记录ID")
    private Long transferId;

    @Schema(description = "调拨单号")
    private String transferNo;

    @Schema(description = "调出门店ID")
    private Long fromStoreId;

    @Schema(description = "调出门店名称")
    private String fromStoreName;

    @Schema(description = "调入门店ID")
    private Long toStoreId;

    @Schema(description = "调入门店名称")
    private String toStoreName;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品规格")
    private String productSpec;

    @Schema(description = "调拨数量")
    private BigDecimal quantity;

    @Schema(description = "拆批数")
    private Integer batchCount;

    @Schema(description = "操作人ID")
    private Long createdBy;

    @Schema(description = "操作人姓名")
    private String createdByName;

    @Schema(description = "调拨时间")
    private LocalDateTime createdAt;

    @Schema(description = "备注")
    private String remark;
}
