package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "目标历史分页结果")
public class GoalPageResult {

    @Schema(description = "记录列表")
    private java.util.List<GoalItem> records;

    @Schema(description = "总条数")
    private long total;

    @Data
    @Schema(description = "目标记录")
    public static class GoalItem {
        @Schema(description = "ID")
        private Long id;

        @Schema(description = "员工ID")
        private Long staffId;

        @Schema(description = "员工姓名")
        private String staffName;

        @Schema(description = "月份")
        private String month;

        @Schema(description = "销售目标")
        private BigDecimal targetSalesAmount;

        @Schema(description = "新客目标")
        private Integer targetNewCustomer;

        @Schema(description = "提成目标")
        private BigDecimal targetCommission;

        @Schema(description = "创建时间")
        private LocalDateTime createdAt;
    }
}
