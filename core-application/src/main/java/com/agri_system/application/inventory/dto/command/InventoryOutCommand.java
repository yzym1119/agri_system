package com.agri_system.application.inventory.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 出库命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "出库命令")
public class InventoryOutCommand {
    
    @NotNull(message = "门店ID不能为空")
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @NotNull(message = "出库数量不能为空")
    @DecimalMin(value = "0.0001", message = "出库数量必须大于0")
    @Schema(description = "出库数量", example = "50.0000")
    private BigDecimal quantity;
    
    @Schema(description = "出库单位（如：箱、瓶、件，用于单位换算）", example = "瓶")
    private String unit;
    
    @Schema(description = "关联类型（如：ORDER-订单）", example = "ORDER")
    private String referenceType;
    
    @Schema(description = "关联ID", example = "123")
    private Long referenceId;

    @Schema(description = "关联订单明细ID，销售出库必填")
    private Long referenceLineId;
    
    @Schema(description = "备注", example = "销售出库")
    private String remark;
}


