package com.agri_system.application.inventory.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "库存位置命令")
public class InventoryLocationCommand {
    @NotNull(message = "门店ID不能为空")
    private Long storeId;
    @NotBlank(message = "位置类型不能为空")
    private String locationType;
    private String code;
    @NotBlank(message = "位置名称不能为空")
    private String name;
}
