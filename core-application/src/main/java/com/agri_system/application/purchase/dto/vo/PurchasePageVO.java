package com.agri_system.application.purchase.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购单分页列表 VO
 *
 * @author AI Generated
 */
@Data
@Schema(description = "采购单分页列表")
public class PurchasePageVO {

    @Schema(description = "采购单ID", example = "1")
    private Long id;

    @Schema(description = "采购单号", example = "PO20240101000001")
    private String orderNo;

    @Schema(description = "门店ID", example = "1")
    private Long storeId;

    @Schema(description = "门店名称", example = "总店")
    private String storeName;

    @Schema(description = "供应商名称", example = "张三农资")
    private String supplierName;

    @Schema(description = "总金额", example = "1050.00")
    private BigDecimal totalAmount;

    @Schema(description = "状态：1-待提交，2-待入库，3-已入库，4-已取消", example = "2")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}










