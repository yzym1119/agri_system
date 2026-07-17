package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单发货命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单发货命令")
public class OrderShipCommand {
    
    @NotNull(message = "订单ID不能为空")
    @Schema(description = "订单ID", example = "1")
    private Long orderId;
    
    @Schema(description = "物流公司名称（快递发货时必填）", example = "顺丰快递")
    private String logisticsCompany;
    
    @Schema(description = "快递单号（快递发货时必填）", example = "SF1234567890")
    private String trackingNo;

    @Schema(description = "配送员ID（同城派单时必填）", example = "1001")
    private Long deliveryStaffId;
}







