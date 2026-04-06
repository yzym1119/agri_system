package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "员工绩效看板")
public class StaffDashboardVO {

    @Schema(description = "本月销售额")
    private BigDecimal monthSales;

    @Schema(description = "本月提成")
    private BigDecimal monthCommission;

    @Schema(description = "新客数量")
    private Integer newCustomerCount;

    @Schema(description = "目标销售额")
    private BigDecimal targetSalesAmount;

    @Schema(description = "目标提成")
    private BigDecimal targetCommission;

    @Schema(description = "目标进度百分比")
    private BigDecimal goalProgress;
}

