package com.agri_system.application.staff.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "角色分配菜单命令")
public class RoleAssignCommand {

    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @Schema(description = "菜单ID列表")
    private List<Long> menuIds;
}





