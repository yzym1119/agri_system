package com.agri_system.application.product.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新商品分类命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "更新商品分类命令")
public class CategoryUpdateCommand {
    
    @NotNull(message = "分类ID不能为空")
    @Schema(description = "分类ID", example = "1")
    private Long id;
    
    @NotBlank(message = "分类名称不能为空")
    @Schema(description = "分类名称", example = "肥料类")
    private String name;
    
    @Schema(description = "父分类ID（为空表示顶级分类）", example = "1")
    private Long parentId;
    
    @Schema(description = "排序顺序", example = "0")
    private Integer sortOrder;
    
    @Schema(description = "状态：1-启用，2-停用", example = "1")
    private Integer status;
    
    @Schema(description = "备注", example = "备注信息")
    private String remark;
}




