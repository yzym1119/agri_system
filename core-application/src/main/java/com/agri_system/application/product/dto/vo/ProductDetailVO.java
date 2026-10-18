package com.agri_system.application.product.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品详情视图对象
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "商品详情视图对象")
public class ProductDetailVO {
    
    @Schema(description = "商品ID", example = "1")
    private Long id;
    
    @Schema(description = "商品编码", example = "P001")
    private String code;
    
    @Schema(description = "商品名称", example = "复合肥料")
    private String name;
    
    @Schema(description = "商品分类ID", example = "1")
    private Long categoryId;
    
    @Schema(description = "分类名称", example = "肥料类")
    private String categoryName;
    
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
    
    @Schema(description = "主辅单位换算比例", example = "20.0000")
    private BigDecimal mainToAuxRatio;
    
    @Schema(description = "零售价（元/基准单位，按商品业务单位系数换算销售价）", example = "50.00")
    private BigDecimal suggestedRetailPrice;

    @Schema(description = "零售价、会员价、最低价对应的持久化基准单位")
    private String pricingUnit;

    /** 实际持久化基准单位；旧档案投影不作为数量证据。 */
    private String baseUnitName;
    private Boolean allowFractionalBaseQuantity;
    
    @Schema(description = "会员价（元/基准单位，0或空表示使用零售价）", example = "45.00")
    private BigDecimal vipPrice;
    
    @Schema(description = "最低价提醒（元/基准单位，不作为销售价兜底）", example = "40.00")
    private BigDecimal minPrice;
    
    @Schema(description = "移动加权平均成本（元，只读）", example = "30.00")
    private BigDecimal avgCostPrice;
    
    @Schema(description = "主图URL", example = "https://example.com/image.jpg")
    private String imageUrl;
    
    @Schema(description = "详情文字（纯文字详情）", example = "本品为高效复合肥料...")
    private String detailText;
    
    @Schema(description = "是否上架：1-上架，0-下架", example = "1")
    private Integer isOnline;
    
    @Schema(description = "线上价（元）", example = "48.00")
    private BigDecimal onlinePrice;
    
    @Schema(description = "基础运费（元）", example = "5.00")
    private BigDecimal baseShippingFee;
    
    @Schema(description = "允许专用金：1-允许，0-不允许", example = "0")
    private Integer allowSpecialFund;
    
    @Schema(description = "已配置价格策略数量（仅统计启用状态）", example = "2")
    private Integer strategyCount;
    
    @Schema(description = "状态：1-正常，2-停用", example = "1")
    private Integer status;
    
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

    @Schema(description = "最低库存预警值", example = "10.00")
    private BigDecimal minStock;

    @Schema(description = "最高库存预警值", example = "500.00")
    private BigDecimal maxStock;

    @Schema(description = "建议补货数量", example = "100.00")
    private BigDecimal reorderQuantity;

    @Schema(description = "单位模型状态：LEGACY、NEW_CHAIN_ENABLED")
    private String unitModelStatus;

    @Schema(description = "SKU 业务单位列表")
    private java.util.List<ProductUnitVO> units;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}










