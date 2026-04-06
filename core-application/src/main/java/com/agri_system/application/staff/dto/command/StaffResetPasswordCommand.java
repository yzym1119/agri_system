package com.agri_system.application.staff.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "员工重置密码命令")
public class StaffResetPasswordCommand {

    @NotNull(message = "员工ID不能为空")
    private Long staffId;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}





