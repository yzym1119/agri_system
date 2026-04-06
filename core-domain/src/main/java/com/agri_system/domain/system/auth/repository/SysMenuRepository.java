package com.agri_system.domain.system.auth.repository;

import com.agri_system.domain.system.auth.model.SysMenu;

import java.util.List;

public interface SysMenuRepository {

    SysMenu save(SysMenu menu);

    void deleteById(Long id);

    List<SysMenu> findAll();

    List<SysMenu> findByIds(List<Long> ids);
    
    /**
     * 检查角色列表是否拥有指定权限
     */
    boolean hasPermission(List<Long> roleIds, String permission);
}





