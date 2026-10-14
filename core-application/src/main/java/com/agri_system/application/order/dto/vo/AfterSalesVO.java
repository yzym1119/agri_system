package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后记录 VO（用于订单详情）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "售后记录")
public class AfterSalesVO {
    
    @Schema(description = "售后单ID", example = "1")
    private Long id;
    
    @Schema(description = "售后单号", example = "AS20240101000001")
    private String afterSalesNo;
    
    @Schema(description = "售后类型：1-客户取消，2-管理端退货", example = "2")
    private Integer type;
    
    @Schema(description = "售后状态：10-已申请，40-待退款，50-已完成，90-已驳回", example = "50")
    private Integer status;
    
    @Schema(description = "退款金额", example = "100.00")
    private BigDecimal refundAmount;
    private BigDecimal creditReductionAmount;
    private BigDecimal cashRefundAmount;
    
    @Schema(description = "售后原因", example = "商品质量问题")
    private String reason;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "退款时间")
    private LocalDateTime refundTime;
}









