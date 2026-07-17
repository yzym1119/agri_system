package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "工资单详情（含提成明细）")
public class StaffSalaryBillDetailVO {

    @Schema(description = "工资单ID")
    private Long id;

    @Schema(description = "员工ID")
    private Long staffId;

    @Schema(description = "员工姓名")
    private String staffName;

    @Schema(description = "月份")
    private String month;

    @Schema(description = "总销售额")
    private BigDecimal totalSales;

    @Schema(description = "总提成")
    private BigDecimal totalCommission;

    @Schema(description = "新客数量")
    private Integer newCustomerCount;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "确认时间")
    private LocalDateTime confirmedAt;

    @Schema(description = "提成明细")
    private List<StaffPerformanceLogVO> lineItems;
}
