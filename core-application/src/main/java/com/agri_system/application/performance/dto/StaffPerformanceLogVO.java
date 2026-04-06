package com.agri_system.application.performance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "绩效流水视图")
public class StaffPerformanceLogVO {

    @Schema(description = "流水ID")
    private Long id;

    @Schema(description = "员工ID")
    private Long staffId;

    @Schema(description = "门店ID")
    private Long storeId;

    @Schema(description = "业务类型")
    private Integer bizType;

    @Schema(description = "业务ID")
    private Long bizId;

    @Schema(description = "订单明细ID")
    private Long orderItemId;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "计算说明")
    private String calcDesc;

    @Schema(description = "是否待复核")
    private Integer needReview;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}

