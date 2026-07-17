package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "提成规则视图")
public class CommissionRuleVO {

    @Schema(description = "规则ID")
    private Long id;

    @Schema(description = "规则类别")
    private Integer ruleCategory;

    @Schema(description = "目标ID")
    private Long targetId;

    @Schema(description = "目标名称")
    private String targetName;

    @Schema(description = "员工ID")
    private Long staffId;

    @Schema(description = "计算模式")
    private String calcMode;

    @Schema(description = "计算值")
    private BigDecimal calcValue;

    @Schema(description = "单行封顶")
    private BigDecimal maxPerItem;

    @Schema(description = "订单封顶")
    private BigDecimal maxPerOrder;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "是否启用")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
