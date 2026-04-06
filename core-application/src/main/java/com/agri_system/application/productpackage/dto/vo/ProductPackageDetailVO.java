package com.agri_system.application.productpackage.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 套餐详情VO（含明细列表）
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "套餐详情VO")
public class ProductPackageDetailVO {
    
    @Schema(description = "套餐ID", example = "1")
    private Long id;
    
    @Schema(description = "套餐编码", example = "PKG202401010001")
    private String code;
    
    @Schema(description = "套餐名称", example = "春季作物解决方案")
    private String name;
    
    @Schema(description = "零售价", example = "200.00")
    private BigDecimal suggestedRetailPrice;
    
    @Schema(description = "VIP价", example = "180.00")
    private BigDecimal vipPrice;
    
    @Schema(description = "最低售价", example = "150.00")
    private BigDecimal minPrice;
    
    @Schema(description = "状态：1=上架，0=下架，-1=删除", example = "1")
    private Integer status;
    
    @Schema(description = "备注", example = "备注信息")
    private String remark;
    
    @Schema(description = "创建时间", example = "2024-01-01T00:00:00")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间", example = "2024-01-01T00:00:00")
    private LocalDateTime updatedAt;
    
    @Schema(description = "套餐明细列表")
    private List<ProductPackageItemVO> items;
    
    @Schema(description = "可销售库存数（理论最大库存，基于子商品库存计算）", example = "10")
    private java.math.BigDecimal stock;
}










