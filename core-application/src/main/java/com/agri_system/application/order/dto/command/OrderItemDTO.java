package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细DTO
 * 
 * ✅ 优化说明（Step 6.7）：
 * - 前端已将套餐炸开为多个商品行
 * - 后端统一处理商品类型（itemType=1）
 * - packageId 字段已废弃，保留用于向后兼容
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单明细")
public class OrderItemDTO {
    
    @Schema(description = "商品ID（必填）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;
    
    @Deprecated
    @Schema(description = "套餐ID（已废弃，前端已将套餐炸开为商品）", example = "1", deprecated = true)
    private Long packageId;
    
    @Deprecated
    @Schema(description = "明细类型（已废弃，统一为1-商品）", example = "1", deprecated = true)
    private Integer itemType;
    
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于0")
    @Schema(description = "数量", example = "10.0000")
    private BigDecimal quantity;
    
    @Schema(description = "单位（可选，如不提供则使用商品主单位）", example = "袋")
    private String unit;
    
    @Schema(description = "单价（元，可选，如果不提供则从价格体系查询）", example = "50.00")
    private BigDecimal unitPrice;

    @Schema(description = "是否库房取货（POS可选，true 时由新链路生成取货请求，不改走即时出库）", example = "false")
    private Boolean warehousePickup;

    @Schema(description = "人工改价原因（POS可选，客户端提交价与服务端报价不一致时随审计一起留痕）", example = "老客户议价")
    private String priceChangeReason;
}








