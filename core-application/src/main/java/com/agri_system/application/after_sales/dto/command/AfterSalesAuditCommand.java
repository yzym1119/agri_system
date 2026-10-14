package com.agri_system.application.after_sales.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 售后审核命令
 */
@Data
@Schema(description = "售后审核命令")
public class AfterSalesAuditCommand {

    @Schema(description = "由请求地址的售后单编号填入", accessMode = Schema.AccessMode.READ_ONLY)
    private Long afterSalesId;

    @NotNull(message = "审核结果不能为空")
    @Schema(description = "是否通过")
    private Boolean approved;

    @Schema(description = "审核备注")
    private String remark;
}








