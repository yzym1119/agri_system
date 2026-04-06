package com.agri_system.application.staff.dto.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 员工列表视图对象
 * 
 * @author AI Generated
 */
@Data
@Schema(description = "员工列表视图对象")
public class StaffListVO {
    
    private Long id;
    private String code;
    private String name;
    private String phone;
    private Long storeId;
    private String storeName;
    private Integer status;
    private List<RoleInfo> roles;
    
    @Data
    @Schema(description = "角色信息")
    public static class RoleInfo {
        private Long id;
        private String name;
        private String code;
    }
}






