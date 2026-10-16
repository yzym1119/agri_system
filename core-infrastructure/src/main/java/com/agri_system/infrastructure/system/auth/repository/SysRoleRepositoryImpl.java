package com.agri_system.infrastructure.system.auth.repository;

import com.agri_system.domain.system.auth.model.SysRole;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.infrastructure.system.auth.mapper.SysRoleMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SysRoleRepositoryImpl implements SysRoleRepository {

    private final SysRoleMapper roleMapper;

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
        QueryWrapper query = QueryWrapper.create()
                .select("r.*")
                .from("sys_role").as("r")
                .innerJoin("sys_staff_role").as("sr").on("r.id = sr.role_id")
                .where("sr.staff_id = ?", staffId).and("r.status = 1");
        List<SysRole> roles = roleMapper.selectListByQuery(query);
        return roles.stream().map(SysRole::getCode).collect(Collectors.toList());
    }
}





