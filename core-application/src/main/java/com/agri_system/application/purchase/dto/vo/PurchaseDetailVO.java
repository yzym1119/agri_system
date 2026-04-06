package com.agri_system.application.purchase.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 采购单详情VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "采购单详情")
public class PurchaseDetailVO {
    
    @Schema(description = "采购单ID", example = "1")
    private Long id;
    
    @Schema(description = "采购单号", example = "PO20240101000001")
    private String orderNo;
    
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "门店名称", example = "XX门店")
    private String storeName;
    
    @Schema(description = "供应商ID", example = "1")
    private Long supplierId;
    
    @Schema(description = "供应商名称", example = "XX供应商")
    private String supplierName;
    
    @Schema(description = "总金额", example = "1050.00")
    private BigDecimal totalAmount;
    
    @Schema(description = "状态：1-待提交，2-待入库，3-已入库，4-已取消", example = "2")
    private Integer status;
    
    @Schema(description = "状态描述", example = "待入库")
    private String statusDesc;
    
    @Schema(description = "备注", example = "采购备注")
    private String remark;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
    
    @Schema(description = "创建人ID", example = "1")
    private Long createdBy;
    
    @Schema(description = "采购明细列表")
    private List<PurchaseItemVO> items;
}

