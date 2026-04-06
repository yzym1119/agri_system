package com.agri_system.infrastructure.system.auth.repository;

import com.agri_system.domain.system.auth.model.SysRoleMenu;
import com.agri_system.domain.system.auth.repository.SysRoleMenuRepository;
import com.agri_system.infrastructure.system.auth.mapper.SysRoleMenuMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SysRoleMenuRepositoryImpl implements SysRoleMenuRepository {

    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public void deleteByRoleId(Long roleId) {
        roleMenuMapper.deleteByQuery(QueryWrapper.create().where("role_id = ?", roleId));
    }

    @Override
    public void batchInsert(Long roleId, List<Long> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return;
        }
        menuIds.forEach(menuId -> {
            SysRoleMenu relation = new SysRoleMenu();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            roleMenuMapper.insert(relation);
        });
    }

    @Override
    public List<Long> findMenuIdsByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 修复：使用循环构建 OR 条件，避免 MyBatis-Flex in 语法问题
        QueryWrapper query = QueryWrapper.create();
        if (roleIds.size() == 1) {
            query.where("role_id = ?", roleIds.get(0));
        } else {
            // 使用 OR 条件组合
            query.where("role_id = ?", roleIds.get(0));
            for (int i = 1; i < roleIds.size(); i++) {
                query.or("role_id = ?", roleIds.get(i));
            }
        }
        return roleMenuMapper.selectListByQuery(query)
                .stream()
                .map(SysRoleMenu::getMenuId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> findMenuIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        QueryWrapper query = QueryWrapper.create().where("role_id = ?", roleId);
        return roleMenuMapper.selectListByQuery(query)
                .stream()
                .map(SysRoleMenu::getMenuId)
                .distinct()
                .collect(Collectors.toList());
    }
}

