package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工月度目标命令")
public class StaffMonthlyGoalCommand {

    @NotNull
    @Schema(description = "员工ID")
    private Long staffId;

    @NotBlank
    @Schema(description = "月份，格式YYYY-MM")
    private String month;

    @Schema(description = "销售目标金额")
    private BigDecimal targetSalesAmount;

    @Schema(description = "新客数量目标")
    private Integer targetNewCustomer;

    @Schema(description = "提成目标")
    private BigDecimal targetCommission;
}

