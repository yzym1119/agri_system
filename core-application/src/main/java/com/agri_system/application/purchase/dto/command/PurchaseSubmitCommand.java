package com.agri_system.application.purchase.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交采购单命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "提交采购单命令")
public class PurchaseSubmitCommand {
    
    @NotNull(message = "采购单ID不能为空")
    @Schema(description = "采购单ID", example = "1")
    private Long orderId;
}

