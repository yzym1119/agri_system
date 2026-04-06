package com.agri_system.application.inventory.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存调整（盘点）命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "库存调整（盘点）命令")
public class InventoryAdjustCommand {
    
    @NotNull(message = "门店ID不能为空")
    @Schema(description = "门店ID", required = true, example = "1")
    private Long storeId;
    
    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", required = true, example = "1")
    private Long productId;
    
    @NotNull(message = "调整数量不能为空")
    @Schema(description = "调整数量（正数为盘盈，负数为盘亏）", required = true, example = "10.00")
    private BigDecimal quantityDiff;
    
    @NotNull(message = "调整类型不能为空")
    @Schema(description = "调整类型：1-盘盈，2-盘亏", required = true, example = "1")
    private Integer type;
    
    @Schema(description = "备注", example = "盘点调整")
    private String remark;
}









