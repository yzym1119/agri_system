package com.agri_system.application.product.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 价格规则视图对象
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "价格规则视图对象")
public class PriceRuleVO {
    
    @Schema(description = "价格规则ID", example = "1")
    private Long id;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "门店ID（为空表示全局价格）", example = "1")
    private Long storeId;
    
    @Schema(description = "客户类型：1-普通客户，2-VIP客户（为空表示通用价格）", example = "2")
    private Integer customerType;
    
    @Schema(description = "VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效）", example = "3")
    private Integer vipLevel;
    
    @Schema(description = "价格类型：1-建议零售价，2-VIP价，3-最低限价，4-进价", example = "2")
    private Integer priceType;
    
    @Schema(description = "价格（元）", example = "45.00")
    private BigDecimal price;
    
    @Schema(description = "生效日期", example = "2024-01-01")
    private LocalDate effectiveDate;
    
    @Schema(description = "失效日期", example = "2024-12-31")
    private LocalDate expiryDate;
    
    @Schema(description = "状态：1-启用，0-停用", example = "1")
    private Integer status;
    
    @Schema(description = "销售单位（为空表示旧链路按主单位换算）", example = "箱")
    private String saleUnit;
    
    @Schema(description = "销售渠道（为空表示通用规则）", example = "POS")
    private String channel;
    
    @Schema(description = "规则版本", example = "1")
    private String ruleVersion;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}










