package com.agri_system.application.customer.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户权益流水VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "客户权益流水")
public class CustomerBenefitLogVO {
    
    @Schema(description = "流水ID", example = "1")
    private Long id;
    
    @Schema(description = "客户ID", example = "1")
    private Long customerId;
    
    @Schema(description = "来源类型：1-每月权益(自动) 2-充值赠送 3-手动发放", example = "2")
    private Integer sourceType;
    
    @Schema(description = "内容快照", example = "专用金¥100，化肥券×1")
    private String benefitContent;
    
    @Schema(description = "关联ID", example = "1")
    private Long associatedId;
    
    @Schema(description = "操作人ID", example = "1")
    private Long createdBy;
    
    @Schema(description = "操作人姓名", example = "张三")
    private String createdByName;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}

