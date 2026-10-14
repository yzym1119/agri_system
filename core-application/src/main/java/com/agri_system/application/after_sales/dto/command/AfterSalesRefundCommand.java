package com.agri_system.application.after_sales.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 售后退款命令
 */
@Data
@Schema(description = "售后退款命令")
public class AfterSalesRefundCommand {
    /** 正式退款必须确认预览；同一预览标识防止网络重试重复退款。 */
    @NotBlank(message = "请先确认最新退货资金预览")
    private String quoteId;


    @Schema(description = "由请求地址的售后单编号填入", accessMode = Schema.AccessMode.READ_ONLY)
    private Long afterSalesId;

    @DecimalMin(value = "0.00", message = "成交冲销金额不能为负数")
    @Schema(description = "退款金额（为空则按售后单金额）")
    private BigDecimal refundAmount;
}








