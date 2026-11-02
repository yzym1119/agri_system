package com.agri_system.application.product.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 更新商品命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "更新商品命令")
public class ProductUpdateCommand {
    
    @Schema(description = "商品ID（从路径参数获取）", example = "1")
    private Long id;

    @Schema(description = "商品名称", example = "复合肥料")
    private String name;
    
    @Schema(description = "商品分类ID", example = "1")
    private Long categoryId;
    
    @Schema(description = "规格（如：500g/袋）", example = "500g/袋")
    private String specification;
    
    @Schema(description = "剂型（如：颗粒剂、水剂、粉剂）", example = "颗粒剂")
    private String dosageForm;
    
    @Schema(description = "生产厂家", example = "XX农资有限公司")
    private String manufacturer;
    
    @Schema(description = "主单位（如：袋、瓶、箱）", example = "袋")
    private String mainUnit;
    
    @Schema(description = "辅单位（如：箱、件）", example = "箱")
    private String auxUnit;
    
    @Schema(description = "主辅单位换算比例（1主单位=？辅单位，如：1箱=20袋，则比例为20）", example = "20.0000")
    private BigDecimal mainToAuxRatio;
    
    @PositiveOrZero(message = "建议零售价必须大于等于0")
    @Schema(description = "零售价（元/基准单位，按商品业务单位系数换算销售价）", example = "50.00")
    private BigDecimal suggestedRetailPrice;
    
    @PositiveOrZero(message = "VIP价格必须大于等于0")
    @Schema(description = "会员价（元/基准单位，0或空表示使用零售价）", example = "45.00")
    private BigDecimal vipPrice;
    
    @PositiveOrZero(message = "最低限价必须大于等于0")
    @Schema(description = "最低价提醒（元/基准单位，不作为销售价兜底）", example = "40.00")
    private BigDecimal minPrice;
    
    @Schema(description = "主图URL", example = "https://example.com/image.jpg")
    private String imageUrl;
    
    @Schema(description = "详情文字（纯文字详情）", example = "本品为高效复合肥料...")
    private String detailText;
    
    @PositiveOrZero(message = "基础运费必须大于等于0")
    @Schema(description = "基础运费（元，默认0）", example = "5.00")
    private BigDecimal baseShippingFee;
    
    @Schema(description = "是否上架：1-上架，0-下架", example = "0")
    private Integer isOnline;
    
    @Schema(description = "允许专用金：1-允许，0-不允许", example = "0")
    private Integer allowSpecialFund;
    
    @Schema(description = "备注", example = "备注信息")
    private String remark;

    @Schema(description = "条形码（EAN-13/Code128）", example = "6901234567890")
    private String barcode;

    @Schema(description = "农药/肥料登记证号", example = "PD20080001")
    private String registrationNo;

    @Schema(description = "存储条件（如：阴凉干燥处、避光保存）", example = "阴凉干燥处")
    private String storageConditions;

    @Schema(description = "保质期（天）", example = "730")
    private Integer shelfLifeDays;

    @Schema(description = "使用方法/稀释比例", example = "兑水1:500喷雾")
    private String usageMethod;

    @PositiveOrZero(message = "最低库存必须大于等于0")
    @Schema(description = "最低库存预警值", example = "10.00")
    private BigDecimal minStock;

    @PositiveOrZero(message = "最高库存必须大于等于0")
    @Schema(description = "最高库存预警值", example = "500.00")
    private BigDecimal maxStock;

    @PositiveOrZero(message = "建议补货量必须大于等于0")
    @Schema(description = "建议补货数量", example = "100.00")
    private BigDecimal reorderQuantity;
}




