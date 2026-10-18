package com.agri_system.application.order.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细VO
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "订单明细")
public class OrderItemVO {
    
    @Schema(description = "明细ID", example = "1")
    private Long id;
    
    @Schema(description = "商品ID", example = "1")
    private Long productId;
    
    @Schema(description = "套餐ID", example = "1")
    private Long packageId;

    /** 同一订单内的套餐销售组；空表示历史行/单品，不能推测历史套数。 */
    private Integer packageGroupNo;
    /** 套餐销售时的名称、套数和每套成交价快照。 */
    private String packageName;
    private BigDecimal packageQuantity;
    private BigDecimal packageUnitPrice;

    
    @Schema(description = "明细类型：1-商品，2-套餐", example = "1")
    private Integer itemType;
    
    @Schema(description = "商品/套餐名称", example = "复合肥")
    private String productName;
    
    @Schema(description = "数量", example = "10.0000")
    private BigDecimal quantity;
    
    @Schema(description = "单位", example = "袋")
    private String unit;

    /** 原出库批次对应的持久化基准单位；无证据时为空。 */
    private String baseUnitName;
    private Boolean allowFractionalBaseQuantity;
    /** 原出库总量/原销售数量精确系数；不能用当前档案猜补。 */
    private BigDecimal originalToBaseRatio;
    
    @Schema(description = "规格", example = "50kg")
    private String specification;
    
    @Schema(description = "厂家/品牌", example = "XX化工")
    private String manufacturer;
    @Schema(description = "单价", example = "50.00")
    private BigDecimal unitPrice;
    
    @Schema(description = "小计金额", example = "500.00")
    private BigDecimal totalPrice;

    @Schema(description = "已退数量", example = "2.0000")
    private BigDecimal refundedQuantity;

    @Schema(description = "可退数量", example = "8.0000")
    private BigDecimal refundableQuantity;

    @Schema(description = "已退金额", example = "100.00")
    private BigDecimal refundedAmount;

    @Schema(description = "可退金额", example = "400.00")
    private BigDecimal refundableAmount;
}







