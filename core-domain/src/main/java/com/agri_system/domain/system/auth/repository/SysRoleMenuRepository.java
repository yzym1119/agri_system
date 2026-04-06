package com.agri_system.domain.system.auth.repository;

import java.util.List;

public interface SysRoleMenuRepository {

    void deleteByRoleId(Long roleId);

    void batchInsert(Long roleId, List<Long> menuIds);

    List<Long> findMenuIdsByRoleIds(List<Long> roleIds);
    
    List<Long> findMenuIdsByRoleId(Long roleId);
}





