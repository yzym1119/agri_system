package com.agri_system.application.marketing.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "优惠券模板")
public class CouponTemplateVO {
    
    @Schema(description = "模板ID", example = "1")
    private Long id;
    
    @Schema(description = "模板名称", example = "化肥券")
    private String name;
    
    @Schema(description = "类型：1-满减券，2-折扣券", example = "1")
    private Integer type;
    
    @Schema(description = "面额/折扣率", example = "100.00")
    private BigDecimal amount;
    
    @Schema(description = "使用门槛（元）", example = "200.00")
    private BigDecimal minOrderAmount;
    
    @Schema(description = "限制分类ID", example = "1")
    private Long limitCategoryId;
    
    @Schema(description = "限制分类名称", example = "化肥")
    private String limitCategoryName;
    
    @Schema(description = "限制商品ID", example = "1")
    private Long limitProductId;
    
    @Schema(description = "有效期类型：1-固定日期范围，2-领取后有效天数", example = "1")
    private Integer validityType;
    
    @Schema(description = "固定有效期开始")
    private LocalDateTime validStartTime;
    
    @Schema(description = "固定有效期结束")
    private LocalDateTime validEndTime;
    
    @Schema(description = "领取后有效天数", example = "30")
    private Integer validDays;
    
    @Schema(description = "发放总量限制（-1不限）", example = "1000")
    private Integer totalCount;
    
    @Schema(description = "状态：1-启用，0-停用", example = "1")
    private Integer status;
    
    @Schema(description = "备注")
    private String remark;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}









