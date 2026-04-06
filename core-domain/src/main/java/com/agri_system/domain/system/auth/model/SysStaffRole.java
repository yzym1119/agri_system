package com.agri_system.domain.system.auth.model;

import com.mybatisflex.annotation.Table;
import lombok.Data;

@Data
@Table("sys_staff_role")
public class SysStaffRole {

    private Long staffId;

    private Long roleId;
}





