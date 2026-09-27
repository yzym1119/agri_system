package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 库房取货请求创建命令。
 *
 * <p>货架余额不足或操作员选择库房取货时创建，由后仓拣货员扫描内部批次标签或
 * 显式确认批次与货位完成履约。</p>
 */
@Data
@Schema(description = "库房取货请求")
public class PickupRequestCommand {

    @Schema(description = "订单ID（管理端由路径参数写入，无需请求体传递）", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long orderId;

    @NotNull(message = "订单明细ID不能为空")
    @Schema(description = "订单明细ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderItemId;

    @Schema(description = "期望拣货的源货位ID（可选，缺省解析门店后仓）")
    private Long sourceLocationId;
}
