package com.agri_system.infrastructure.system.auth.repository;

import com.agri_system.domain.system.auth.model.SysRole;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.infrastructure.system.auth.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SysRoleRepositoryImpl implements SysRoleRepository {

    private final SysRoleMapper roleMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Override
    public SysRole save(SysRole role) {
        if (role.getId() == null) {
            roleMapper.insert(role);
        } else {
            roleMapper.update(role);
        }
        return role;
    }

    @Override
    public void deleteById(Long id) {
        roleMapper.deleteById(id);
    }

    @Override
    public Optional<SysRole> findById(Long id) {
        return Optional.ofNullable(roleMapper.selectOneById(id));
    }

    @Override
    public List<SysRole> findAll() {
        return roleMapper.selectAll();
    }

    @Override
    public void lockAdministratorRoles() {
        roleMapper.lockAdministratorRoles();
    }

    @Override
    public List<Long> findActiveAdministratorStaffIdsForUpdate() {
        return roleMapper.selectActiveAdministratorStaffIdsForUpdate();
    }

    @Override
    public List<String> findRoleCodesByStaffId(Long staffId) {
        if (staffId == null) {
            return Collections.emptyList();
        }
        // 同身份读取一样可能处于外层Flex DTO投影的租户回调中，不能继承其映射类型。
        return jdbc.queryForList("SELECT r.code FROM sys_role r JOIN sys_staff_role sr ON r.id=sr.role_id "
                + "WHERE sr.staff_id=? AND r.status=1",String.class,staffId);
    }
}





