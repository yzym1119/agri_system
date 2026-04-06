package com.agri_system.application.system.dict.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DictDataUpdateCommand {

    @NotNull(message = "ID不能为空")
    private Long id;

    @NotBlank(message = "字典编码不能为空")
    private String typeCode;

    @NotBlank(message = "显示名称不能为空")
    private String label;

    @NotBlank(message = "存储值不能为空")
    private String value;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder;

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotNull(message = "是否默认不能为空")
    private Integer isDefault;
}






