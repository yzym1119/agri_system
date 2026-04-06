package com.agri_system.application.staff.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "角色保存命令")
public class RoleSaveCommand {

    private Long id;

    @NotBlank(message = "角色名称不能为空")
    private String name;

    @Schema(description = "角色编码（为空时自动生成）")
    private String code;

    @Schema(description = "状态：1-启用 0-停用")
    private Integer status = 1;

    @Schema(description = "绑定的菜单ID列表")
    private List<Long> menuIds;
}





