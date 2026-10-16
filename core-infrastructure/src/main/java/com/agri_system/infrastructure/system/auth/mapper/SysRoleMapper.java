package com.agri_system.infrastructure.system.auth.mapper;

import com.agri_system.domain.system.auth.model.SysRole;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    @Select("SELECT id FROM sys_role WHERE code IN ('ADMIN','SUPER_ADMIN') ORDER BY id FOR UPDATE")
    List<Long> lockAdministratorRoles();

    @Select("SELECT s.id FROM staff s JOIN sys_staff_role sr ON sr.staff_id=s.id "
            + "JOIN sys_role r ON r.id=sr.role_id WHERE s.status=1 AND s.deleted=0 "
            + "AND r.status=1 AND r.code IN ('ADMIN','SUPER_ADMIN') ORDER BY s.id,r.id FOR UPDATE")
    List<Long> selectActiveAdministratorStaffIdsForUpdate();
}





