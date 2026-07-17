package com.agri_system.application.store.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 门店查询条件
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "门店查询条件")
public class StoreQuery {
    
    @Schema(description = "门店编码（模糊查询）", example = "STORE001")
    private String code;
    
    @Schema(description = "门店名称（模糊查询）", example = "XX农资店")
    private String name;
    
    @Schema(description = "状态：1-启用，2-停用", example = "1")
    private Integer status;
    
    @Schema(description = "是否支持自提：1-支持，0-不支持", example = "1")
    private Integer supportPickup;
    
    @Schema(description = "页码（从1开始）", example = "1")
    private Integer pageNum = 1;
    
    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;
}









