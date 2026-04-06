package com.agri_system.application.order.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 订单分页查询条件
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单分页查询条件")
public class OrderPageQuery {
    
    @Schema(description = "页码（从1开始）", example = "1")
    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum = 1;
    
    @Schema(description = "每页大小", example = "10")
    @Min(value = 1, message = "每页大小必须大于0")
    private Integer pageSize = 10;
    
    @Schema(description = "关键词（订单号/手机号/收货人）", example = "ORD2024")
    private String keyword;
    
    @Schema(description = "客户ID（可选）", example = "123")
    private Long customerId;
    
    @Schema(description = "门店ID（可选）", example = "1")
    private Long storeId;
    
    @Schema(description = "订单类型：1-POS，2-小程序自提，3-小程序配送", example = "1")
    private Integer orderType;
    
    @Schema(description = "配送方式：1-自提，2-同城配送，3-快递", example = "1")
    private Integer deliveryType;
    
    @Schema(description = "订单状态：1-待支付，2-已支付，3-待自提，4-待发货，5-已发货，6-已完成，7-已取消，8-已退款，9-部分退款。支持多状态查询，用逗号分隔，如：7,8,9", example = "2")
    private String orderStatus;
    
    @Schema(description = "开始时间（格式：YYYY-MM-DDTHH:mm:ss）", example = "2024-01-01T00:00:00")
    private String startTime;
    
    @Schema(description = "结束时间（格式：YYYY-MM-DDTHH:mm:ss）", example = "2024-12-31T23:59:59")
    private String endTime;
}

