package com.agri_system.application.purchase.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建采购单命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "创建采购单命令")
public class PurchaseCreateCommand {
    
    @NotNull(message = "门店ID不能为空")
    @Schema(description = "入库门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "供应商ID（必填）", example = "1")
    private Long supplierId;

    @Schema(description = "供应商名称", example = "张三农资")
    private String supplierName;
    
    @NotEmpty(message = "采购明细不能为空")
    @Valid
    @Schema(description = "采购明细列表")
    private List<PurchaseItemDTO> items;
    
    @Schema(description = "备注", example = "采购备注")
    private String remark;
}

