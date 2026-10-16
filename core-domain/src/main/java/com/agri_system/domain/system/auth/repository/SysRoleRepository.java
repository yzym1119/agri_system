package com.agri_system.domain.system.auth.repository;

import com.agri_system.domain.system.auth.model.SysRole;

import java.util.List;
import java.util.Optional;

public interface SysRoleRepository {

    SysRole save(SysRole role);

    void deleteById(Long id);

    Optional<SysRole> findById(Long id);

    List<SysRole> findAll();

    List<String> findRoleCodesByStaffId(Long staffId);

    /** 权限低频变更共用管理员角色稳定顺序锁，不依赖门店查询范围。 */
    void lockAdministratorRoles();

    /** 当前读核对真实在职、未删除且具有启用管理员角色的员工。 */
    List<Long> findActiveAdministratorStaffIdsForUpdate();
}





