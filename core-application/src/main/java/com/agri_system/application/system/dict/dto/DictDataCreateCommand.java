package com.agri_system.application.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DictDataCreateCommand {

    @NotBlank(message = "字典编码不能为空")
    private String typeCode;

    @NotBlank(message = "显示名称不能为空")
    private String label;

    @NotBlank(message = "存储值不能为空")
    private String value;

    private Integer sortOrder = 0;

    @NotNull(message = "状态不能为空")
    private Integer status = 1;

    private Integer isDefault = 0;
}






