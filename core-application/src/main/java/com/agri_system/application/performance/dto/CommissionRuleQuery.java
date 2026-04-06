package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "提成规则查询条件")
public class CommissionRuleQuery {

    @Schema(description = "规则类别")
    private Integer ruleCategory;

    @Schema(description = "目标ID")
    private Long targetId;

    @Schema(description = "员工ID")
    private Long staffId;

    @Schema(description = "启用状态：1-启用 0-停用")
    private Integer enabled;
}

