package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "创建/更新提成规则命令")
public class CommissionRuleCreateCommand {

    @Schema(description = "规则ID，更新时必填")
    private Long id;

    @NotNull
    @Schema(description = "规则类别：1-销售 2-充值 3-拉新 4-品牌推广 5-套餐")
    private Integer ruleCategory;

    @Schema(description = "目标ID（商品/套餐/充值规则等）", example = "1001")
    private Long targetId;

    @Schema(description = "指定员工（为空表示通用）", example = "2001")
    private Long staffId;

    @NotNull
    @Schema(description = "计算模式：FIXED/PROFIT_PERCENT/SALES_PERCENT")
    private String calcMode;

    @NotNull
    @Schema(description = "计算值：固定金额或比例（小数）", example = "0.15")
    private BigDecimal calcValue;

    @Schema(description = "单行封顶金额")
    private BigDecimal maxPerItem;

    @Schema(description = "单订单封顶金额")
    private BigDecimal maxPerOrder;

    @Schema(description = "优先级，越大越优先")
    private Integer priority;

    @Schema(description = "是否启用", defaultValue = "true")
    private Boolean enabled = Boolean.TRUE;

    @Schema(description = "备注")
    private String remark;
}

