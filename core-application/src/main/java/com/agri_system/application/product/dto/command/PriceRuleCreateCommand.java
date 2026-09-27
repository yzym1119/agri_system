package com.agri_system.application.product.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建价格规则命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "创建价格规则命令")
public class PriceRuleCreateCommand {
    
    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "门店ID（为空表示全局价格）", example = "1")
    private Long storeId;
    
    @Schema(description = "客户类型：1-普通客户，2-VIP客户（为空表示通用价格）", example = "2")
    private Integer customerType;
    
    @Schema(description = "VIP等级：1-普通VIP，2-银卡VIP，3-金卡VIP，4-钻石VIP（仅VIP客户有效）", example = "3")
    private Integer vipLevel;
    
    @NotNull(message = "价格类型不能为空")
    @Schema(description = "价格类型：1-建议零售价，2-VIP价，3-最低限价，4-进价", example = "2")
    private Integer priceType;
    
    @NotNull(message = "价格不能为空")
    @PositiveOrZero(message = "价格必须大于等于0")
    @Schema(description = "价格（元）", example = "45.00")
    private BigDecimal price;
    
    @Schema(description = "生效日期", example = "2024-01-01")
    private LocalDate effectiveDate;
    
    @Schema(description = "失效日期", example = "2024-12-31")
    private LocalDate expiryDate;

    @Schema(description = "销售单位（可选，为空表示旧链路按主单位换算）", example = "箱")
    private String saleUnit;

    @Schema(description = "销售渠道（可选，为空表示通用规则）", example = "POS")
    private String channel;

    @Schema(description = "规则版本（可选，为空默认 1）", example = "1")
    private String ruleVersion;

    @Schema(description = "状态：1-启用，0-停用", example = "1")
    private Integer status;
}



