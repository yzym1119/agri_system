package com.agri_system.infrastructure.system.auth.repository;

import com.agri_system.domain.system.auth.model.SysMenu;
import com.agri_system.domain.system.auth.repository.SysMenuRepository;
import com.agri_system.domain.system.auth.repository.SysRoleMenuRepository;
import com.agri_system.infrastructure.system.auth.mapper.SysMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SysMenuRepositoryImpl implements SysMenuRepository {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuRepository roleMenuRepository;

    @Override
    public SysMenu save(SysMenu menu) {
        if (menu.getId() == null) {
            menuMapper.insert(menu);
        } else {
            menuMapper.update(menu);
        }
        return menu;
    }

    @Override
    public void deleteById(Long id) {
        menuMapper.deleteById(id);
    }

    @Override
    public List<SysMenu> findAll() {
        QueryWrapper query = QueryWrapper.create().orderBy("sort_order ASC");
        return menuMapper.selectListByQuery(query);
    }

    @Override
    public List<SysMenu> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 修复：使用循环构建 OR 条件，避免 MyBatis-Flex in 语法问题
        QueryWrapper query = QueryWrapper.create();
        if (ids.size() == 1) {
            query.where("id = ?", ids.get(0));
        } else {
            // 使用 OR 条件组合
            query.where("id = ?", ids.get(0));
            for (int i = 1; i < ids.size(); i++) {
                query.or("id = ?", ids.get(i));
            }
        }
        return menuMapper.selectListByQuery(query);
    }

    @Override
    public boolean hasPermission(List<Long> roleIds, String permission) {
        if (roleIds == null || roleIds.isEmpty() || permission == null || permission.isEmpty()) {
            return false;
        }
        // 获取角色拥有的所有菜单ID
        List<Long> menuIds = roleMenuRepository.findMenuIdsByRoleIds(roleIds);
        if (menuIds.isEmpty()) {
            return false;
        }
        // 查找拥有该权限的菜单
        QueryWrapper query = QueryWrapper.create().where("permission = ?", permission);
        if (menuIds.size() == 1) {
            query.and("id = ?", menuIds.get(0));
        } else {
            query.and("id = ?", menuIds.get(0));
            for (int i = 1; i < menuIds.size(); i++) {
                query.or("id = ?", menuIds.get(i));
            }
        }
        return menuMapper.selectCountByQuery(query) > 0;
    }
}





