package com.agri_system.domain.system.auth.model;

import com.mybatisflex.annotation.Table;
import lombok.Data;

@Data
@Table("sys_role_menu")
public class SysRoleMenu {

    private Long roleId;

    private Long menuId;
}





