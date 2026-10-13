package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 单品直接提交；套餐仅提交套餐ID、套数与报价指纹，由后端生成实际物料行。 */
@Data
@Schema(description = "订单明细")
public class OrderItemDTO {
    
    @Schema(description = "商品ID（单品必填，套餐为空）", example = "1")
    private Long productId;
    
    @Schema(description = "套餐ID（itemType=2必填，整套提交，由后端展开）", example = "1")
    private Long packageId;
    
    @Schema(description = "明细类型：1商品、2套餐，默认商品", example = "1")
    private Integer itemType;
    
    @NotNull(message = "数量不能为空")
    @DecimalMin(value = "0.0001", message = "数量必须大于0")
    @Schema(description = "数量", example = "10.0000")
    private BigDecimal quantity;
    
    @Schema(description = "单位（可选，如不提供则使用商品主单位）", example = "袋")
    private String unit;
    
    @Schema(description = "成交单价（单品元/销售单位，套餐元/套；可选，默认后端报价）", example = "50.00")
    private BigDecimal unitPrice;

    @Schema(description = "收银台最近展示的服务端报价；价格变化时要求重新报价，不作为成交价来源")
    private BigDecimal quotedUnitPrice;

    @Schema(description = "套餐展示报价的内容指纹；价格或组成变化时要求重新确认")
    private String packageQuoteId;

    @Schema(description = "是否库房取货（POS可选，true 时由新链路生成取货请求，不改走即时出库）", example = "false")
    private Boolean warehousePickup;

    @Schema(description = "人工改价原因（POS可选，套餐或单品议价时随审计一起留痕）", example = "老客户议价")
    private String priceChangeReason;
}
