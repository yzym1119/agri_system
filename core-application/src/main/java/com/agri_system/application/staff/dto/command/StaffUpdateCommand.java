package com.agri_system.application.staff.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "员工更新命令")
public class StaffUpdateCommand {

    @NotNull(message = "员工ID不能为空")
    private Long id;

    @NotBlank(message = "员工姓名不能为空")
    private String name;

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotNull(message = "所属门店不能为空")
    private Long storeId;

    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID（单选）")
    private Long roleId;

    @Schema(description = "状态：1-在职 0-禁用")
    private Integer status;
}





