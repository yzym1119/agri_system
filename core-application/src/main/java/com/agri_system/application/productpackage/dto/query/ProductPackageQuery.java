package com.agri_system.application.productpackage.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 套餐查询条件
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "套餐查询条件")
public class ProductPackageQuery {
    
    @Schema(description = "套餐编码（模糊查询）", example = "PKG20240101")
    private String code;
    
    @Schema(description = "套餐名称（模糊查询）", example = "春季")
    private String name;
    
    @Schema(description = "状态：1=上架，0=下架", example = "1")
    private Integer status;
    
    @Schema(description = "门店ID（用于计算套餐库存，可选）", example = "1")
    private Long storeId;
    
    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;
}










