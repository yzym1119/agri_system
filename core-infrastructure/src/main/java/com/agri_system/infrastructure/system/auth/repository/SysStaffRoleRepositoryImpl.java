package com.agri_system.infrastructure.system.auth.repository;

import com.agri_system.domain.system.auth.model.SysStaffRole;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import com.agri_system.infrastructure.system.auth.mapper.SysStaffRoleMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SysStaffRoleRepositoryImpl implements SysStaffRoleRepository {

    private final SysStaffRoleMapper staffRoleMapper;

    @Override
    public void deleteByStaffId(Long staffId) {
        staffRoleMapper.deleteByQuery(QueryWrapper.create().where("staff_id = ?", staffId));
    }

    @Override
    public void batchInsert(Long staffId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        roleIds.forEach(roleId -> {
            SysStaffRole relation = new SysStaffRole();
            relation.setStaffId(staffId);
            relation.setRoleId(roleId);
            staffRoleMapper.insert(relation);
        });
    }

    @Override
    public List<Long> findRoleIdsByStaffId(Long staffId) {
        if (staffId == null) {
            return Collections.emptyList();
        }
        QueryWrapper query = QueryWrapper.create().where("staff_id = ?", staffId);
        return staffRoleMapper.selectListByQuery(query)
                .stream()
                .map(SysStaffRole::getRoleId)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteByRoleId(Long roleId) {
        staffRoleMapper.deleteByQuery(QueryWrapper.create().where("role_id = ?", roleId));
    }

    @Override
    public List<Long> findStaffIdsByRoleId(Long roleId) {
        if (roleId == null) {
            return Collections.emptyList();
        }
        QueryWrapper query = QueryWrapper.create().where("role_id = ?", roleId);
        return staffRoleMapper.selectListByQuery(query)
                .stream()
                .map(SysStaffRole::getStaffId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public long countByRoleId(Long roleId) {
        if (roleId == null) {
            return 0;
        }
        QueryWrapper query = QueryWrapper.create().where("role_id = ?", roleId);
        return staffRoleMapper.selectCountByQuery(query);
    }
}

