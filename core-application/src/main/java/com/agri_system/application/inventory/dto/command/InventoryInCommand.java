package com.agri_system.application.inventory.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "入库命令")
public class InventoryInCommand {
    
    @NotNull(message = "门店ID不能为空")
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @NotBlank(message = "批次号不能为空")
    @Schema(description = "批次号（手动输入或系统生成）", example = "BATCH20240101001")
    private String batchNo;
    
    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.0001", message = "入库数量必须大于0")
    @Schema(description = "入库数量", example = "100.0000")
    private BigDecimal quantity;
    
    @NotNull(message = "成本价不能为空")
    @DecimalMin(value = "0.01", message = "成本价必须大于0")
    @Schema(description = "本次进价（元）", example = "10.50")
    private BigDecimal costPrice;
    
    @Schema(description = "生产日期（可选）", example = "2024-01-01")
    private LocalDate productionDate;
    
    @Schema(description = "有效期至（可选）", example = "2025-01-01")
    private LocalDate expireDate;
    
    @Schema(description = "来源类型：PURCHASE-采购，TRANSFER_IN-调拨入库，ADJUSTMENT-盘盈等", example = "PURCHASE")
    private String sourceType;
    
    @Schema(description = "来源单据ID", example = "1")
    private Long sourceId;
    
    @Schema(description = "来源单据行号", example = "1")
    private Integer sourceLineNo;
    
    @Schema(description = "备注", example = "采购入库")
    private String remark;
}

