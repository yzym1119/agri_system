package com.agri_system.application.store.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店详情视图对象
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "门店详情视图对象")
public class StoreDetailVO {
    
    @Schema(description = "门店ID", example = "1")
    private Long id;
    
    @Schema(description = "门店编码", example = "STORE001")
    private String code;
    
    @Schema(description = "门店名称", example = "XX农资店")
    private String name;
    
    @Schema(description = "联系电话", example = "13800138000")
    private String phone;
    
    @Schema(description = "详细地址", example = "XX省XX市XX区XX路XX号")
    private String address;
    
    @Schema(description = "店长ID", example = "1")
    private Long managerId;
    
    @Schema(description = "店长姓名", example = "张三")
    private String managerName;
    
    @Schema(description = "状态：1-启用，0-停用", example = "1")
    private Integer status;
    
    @Schema(description = "是否支持自提：1-支持，0-不支持", example = "1")
    private Integer supportPickup;
    
    @Schema(description = "备注", example = "备注信息")
    private String remark;
    
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
    
    @Schema(description = "创建人ID", example = "1")
    private Long createdBy;
    
    @Schema(description = "更新人ID", example = "1")
    private Long updatedBy;
}










