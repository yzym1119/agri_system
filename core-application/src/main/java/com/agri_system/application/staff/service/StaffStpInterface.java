package com.agri_system.application.staff.service;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.exception.NotLoginException;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StaffStpInterface implements StpInterface {

    private final StaffAuthorityService authorityService;
    private final StaffRepository staffRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long staffId = resolveStaffId(loginId);
        if (staffId == null) {
            return Collections.emptyList();
        }
        return authorityService.loadPermissions(staffId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long staffId = resolveStaffId(loginId);
        if (staffId == null) {
            return Collections.emptyList();
        }
        return authorityService.loadRoleCodes(staffId);
    }

    private Long resolveStaffId(Object loginId) {
        if (loginId == null) {
            return null;
        }
        String value = loginId.toString();
        if (!value.startsWith("STAFF_")) {
            return null;
        }
        try {
            Long id = Long.parseLong(value.substring("STAFF_".length()));
            var staff = staffRepository.findIdentityById(id).orElse(null);
            if (staff == null || !staff.isActive() || staff.hasBeenDeleted()) {
                throw NotLoginException.newInstance("STAFF", NotLoginException.KICK_OUT, "员工登录已失效", null);
            }
            return id;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}





