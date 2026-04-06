package com.agri_system.application.staff.dto.vo;

import com.agri_system.application.performance.dto.StaffDashboardVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "员工档案视图对象")
public class StaffProfileVO {

    private Long id;
    private String code;
    private String name;
    private String phone;
    private Long storeId;
    private Integer status;
    private List<String> roles;
    private List<Long> roleIds;
    private StaffDashboardVO performance;
}





