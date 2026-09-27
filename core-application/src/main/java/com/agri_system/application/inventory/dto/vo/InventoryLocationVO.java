package com.agri_system.application.inventory.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 货位VO（零售事实链 Task9：管理端货位作业界面）。
 */
@Data
@Schema(description = "货位VO")
public class InventoryLocationVO {

    @Schema(description = "货位ID")
    private Long id;

    @Schema(description = "门店ID")
    private Long storeId;

    @Schema(description = "货位类型：SALES_FLOOR/STORE_BACKROOM/CENTRAL_WAREHOUSE/OTHER_STORE")
    private String locationType;

    @Schema(description = "货位编码（门店内唯一，扫码解析用）")
    private String code;

    @Schema(description = "货位名称")
    private String name;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
