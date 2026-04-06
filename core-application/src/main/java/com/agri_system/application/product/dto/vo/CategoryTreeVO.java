package com.agri_system.application.product.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品分类树视图对象
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "商品分类树视图对象")
public class CategoryTreeVO {
    
    @Schema(description = "分类ID", example = "1")
    private Long id;
    
    @Schema(description = "分类编码", example = "CAT001")
    private String code;
    
    @Schema(description = "分类名称", example = "肥料类")
    private String name;
    
    @Schema(description = "父分类ID", example = "0")
    private Long parentId;
    
    @Schema(description = "父分类名称", example = "肥料类")
    private String parentName;
    
    @Schema(description = "分类层级", example = "1")
    private Integer level;
    
    @Schema(description = "排序顺序", example = "0")
    private Integer sortOrder;
    
    @Schema(description = "状态：1-正常，2-停用", example = "1")
    private Integer status;
    
    @Schema(description = "备注", example = "备注信息")
    private String remark;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "子分类列表")
    private List<CategoryTreeVO> children = new ArrayList<>();
}











