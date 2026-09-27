package com.agri_system.application.inventory.dto.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 库存风险人工决策命令（零售事实链 Task8）。
 *
 * <p>只记录人工决定的留痕，绝不触发任何自动动作（不自动禁售/报损/调拨/采购/改价）。</p>
 */
@Data
@Schema(description = "库存风险人工决策命令")
public class InventoryDecisionCommand {

    @NotBlank(message = "风险类型不能为空")
    @Schema(description = "风险类型：DATE_UNKNOWN/EXPIRING/EXPIRED/LOW_STOCK/OVERSTOCK/LOW_MARGIN/DATA_INSUFFICIENT",
            example = "EXPIRING")
    private String riskKind;

    @NotBlank(message = "实体类型不能为空")
    @Schema(description = "实体类型：PRODUCT/BATCH", example = "BATCH")
    private String entityType;

    @NotNull(message = "实体ID不能为空")
    @Schema(description = "实体ID（商品ID或收货批次ID）", example = "61")
    private Long entityId;

    @Schema(description = "系统建议动作（可空，仅原样回显留痕）", example = "INSPECT")
    private String suggestedAction;

    @NotBlank(message = "决策动作不能为空")
    @Schema(description = "人工决策动作：CONTINUE_SALE/INSPECT/REPLENISH/TRANSFER/RETURN_TO_VENDOR/WRITE_OFF/PAUSE_REPLENISHMENT/NO_ACTION",
            example = "TRANSFER")
    private String decisionAction;

    @NotBlank(message = "决策原因不能为空")
    @Size(max = 1000, message = "决策原因不能超过1000字")
    @Schema(description = "人工决策原因", example = "临期批次转门店B促销")
    private String decisionReason;

    /** 确认人：接口不开放（防止伪造确认），留待双人复核流程使用。 */
    @JsonIgnore
    @Schema(hidden = true)
    private Long confirmerId;
}
