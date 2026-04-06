package com.agri_system.application.staff.dto.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 员工查询参数
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "员工查询参数")
public class StaffQuery {
    
    @Schema(description = "员工编码（模糊查询）")
    private String code;
    
    @Schema(description = "员工姓名（模糊查询）")
    private String name;
    
    @Schema(description = "所属门店ID")
    private Long storeId;
    
    @Schema(description = "状态：1-在职，2-离职")
    private Integer status;
    
    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;
    
    @Schema(description = "每页数量", example = "10")
    private Integer pageSize = 10;
}






