package com.agri_system.application.product.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品查询条件
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "商品查询条件")
public class ProductQuery {
    
    @Schema(description = "商品编码（模糊查询）", example = "P001")
    private String code;
    
    @Schema(description = "商品名称（模糊查询）", example = "复合肥料")
    private String name;
    
    @Schema(description = "分类ID", example = "1")
    private Long categoryId;
    
    @Schema(description = "上架状态：1-上架，0-下架", example = "1")
    private Integer isOnline;

    @Schema(description = "商品状态：1-正常，2-停用", example = "1")
    private Integer status;
    
    @Schema(description = "创建时间开始", example = "2024-01-01T00:00:00")
    private LocalDateTime createdStartTime;
    
    @Schema(description = "创建时间结束", example = "2024-12-31T23:59:59")
    private LocalDateTime createdEndTime;
    
    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;
}











