package com.agri_system.application.staff.service;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StaffStpInterface implements StpInterface {

    private final StaffAuthorityService authorityService;

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
            return Long.parseLong(value.substring("STAFF_".length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}





