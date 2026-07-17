package com.agri_system.application.after_sales.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "售后分页查询条件")
public class AfterSalesPageQuery {

    @Schema(description = "页码（从1开始）", example = "1")
    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    @Min(value = 1, message = "每页大小必须大于0")
    private Integer pageSize = 10;

    @Schema(description = "关键词（售后单号/订单号/客户名/手机号）", example = "AS202604")
    private String keyword;

    @Schema(description = "门店ID", example = "1")
    private Long storeId;

    @Schema(description = "售后类型：1-客户取消，2-管理端退货", example = "2")
    private Integer type;

    @Schema(description = "售后状态：10-已申请，40-待退款，50-已完成，90-已驳回", example = "10")
    private Integer status;

    @Schema(description = "开始时间（格式：YYYY-MM-DDTHH:mm:ss）", example = "2026-04-01T00:00:00")
    private String startTime;

    @Schema(description = "结束时间（格式：YYYY-MM-DDTHH:mm:ss）", example = "2026-04-14T23:59:59")
    private String endTime;
}
