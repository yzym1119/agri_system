package com.agri_system.application.inventory.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 调拨命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "调拨命令")
public class InventoryTransferCommand {
    
    @NotNull(message = "调出门店ID不能为空")
    @Schema(description = "调出门店ID", example = "1")
    private Long fromStoreId;
    
    @NotNull(message = "调入门店ID不能为空")
    @Schema(description = "调入门店ID", example = "2")
    private Long toStoreId;
    
    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @NotNull(message = "调拨数量不能为空")
    @DecimalMin(value = "0.0001", message = "调拨数量必须大于0")
    @Schema(description = "调拨数量", example = "30.0000")
    private BigDecimal quantity;
    
    @Schema(description = "备注", example = "门店间调拨")
    private String remark;
}


