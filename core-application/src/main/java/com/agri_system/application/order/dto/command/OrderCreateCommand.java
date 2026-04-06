package com.agri_system.application.order.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 订单创建命令（小程序）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单创建命令")
public class OrderCreateCommand {
    
    @NotNull(message = "门店ID不能为空")
    @Schema(description = "门店ID", example = "1")
    private Long storeId;
    
    @Schema(description = "客户ID（可为空，支持散客）", example = "1")
    private Long customerId;
    
    @NotNull(message = "订单类型不能为空")
    @Schema(description = "订单类型：2-小程序自提，3-小程序配送", example = "2")
    private Integer orderType;
    
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    @Schema(description = "订单明细列表")
    private List<OrderItemDTO> items;
    
    @Schema(description = "优惠券ID", example = "1")
    private Long couponId;
    
    @Schema(description = "配送地址（配送订单必填）", example = "北京市朝阳区xxx")
    private String deliveryAddress;
    
    @Schema(description = "配送联系电话（配送订单必填）", example = "13800138000")
    private String deliveryPhone;
    
    @Schema(description = "备注", example = "请尽快发货")
    private String remark;
}








