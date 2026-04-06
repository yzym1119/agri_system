package com.agri_system.application.store.dto.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建门店命令
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "创建门店命令")
public class StoreCreateCommand {
    
    @Schema(description = "门店编码（唯一，为空时自动生成）", example = "STORE001")
    private String code;
    
    @NotBlank(message = "门店名称不能为空")
    @Schema(description = "门店名称", example = "XX农资店")
    private String name;
    
    @NotBlank(message = "联系电话不能为空")
    @Schema(description = "联系电话（必填）", example = "13800138000")
    private String phone;
    
    @Schema(description = "详细地址", example = "XX省XX市XX区XX路XX号")
    private String address;
    
    @Schema(description = "店长ID（可空）", example = "1")
    private Long managerId;
    
    @Schema(description = "是否支持自提：1-支持，0-不支持", example = "1")
    private Integer supportPickup;
    
    @Schema(description = "状态：1-启用，0-停用", example = "1")
    private Integer status;
    
    @Schema(description = "备注", example = "备注信息")
    private String remark;
}



