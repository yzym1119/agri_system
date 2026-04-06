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
}





