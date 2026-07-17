package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.performance.dto.StaffCommissionDashboardVO;
import com.agri_system.application.performance.service.StaffCommissionDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 员工提成看板Controller
 * 让员工实时看到自己的收入，激发主动性
 */
@RestController
@RequestMapping("/api/admin/staff/dashboard")
@Tag(name = "员工提成看板", description = "实时提成、月度目标进度、预估工资")
@RequiredArgsConstructor
public class StaffDashboardController {

    private final StaffCommissionDashboardService dashboardService;

    @GetMapping
    @Operation(summary = "获取我的提成看板数据")
    public ResponseEntity<StaffCommissionDashboardVO> myDashboard() {
        Long staffId = extractStaffId();
        return ResponseEntity.ok(dashboardService.getDashboard(staffId));
    }

    private Long extractStaffId() {
        String loginId = StpUtil.getLoginIdAsString();
        return Long.parseLong(loginId.replace("STAFF_", ""));
    }
}
