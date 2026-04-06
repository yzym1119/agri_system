package com.agri_system.domain.system.auth.repository;

import java.util.List;

public interface SysStaffRoleRepository {

    void deleteByStaffId(Long staffId);

    void batchInsert(Long staffId, List<Long> roleIds);

    List<Long> findRoleIdsByStaffId(Long staffId);

    void deleteByRoleId(Long roleId);
    
    /**
     * 查询拥有指定角色的员工ID列表
     */
    List<Long> findStaffIdsByRoleId(Long roleId);
    
    /**
     * 统计拥有指定角色的员工数量
     */
    long countByRoleId(Long roleId);
}

