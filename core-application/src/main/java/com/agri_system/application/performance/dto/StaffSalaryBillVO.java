package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "员工工资单")
public class StaffSalaryBillVO {

    @Schema(description = "工资单ID")
    private Long id;

    @Schema(description = "员工ID")
    private Long staffId;

    @Schema(description = "月份")
    private String month;

    @Schema(description = "总提成")
    private BigDecimal totalCommission;

    @Schema(description = "总销售额")
    private BigDecimal totalSales;

    @Schema(description = "新客数量")
    private Integer newCustomerCount;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "确认时间")
    private LocalDateTime confirmedAt;
}

