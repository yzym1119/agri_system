package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.performance.dto.StaffDashboardVO;
import com.agri_system.application.performance.dto.StaffPerformanceLogVO;
import com.agri_system.application.performance.service.StaffPerformanceApplicationService;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "PerformanceStaff", description = "员工绩效看板接口")
@RestController
@RequestMapping("/api/staff/performance")
@RequiredArgsConstructor
public class PerformanceStaffController {

    private final StaffPerformanceApplicationService staffPerformanceApplicationService;

    @GetMapping("/dashboard")
    @SaCheckPermission("performance:staff:view")
    @Operation(summary = "我的绩效看板")
    public R<StaffDashboardVO> dashboard(@RequestParam Long staffId) {
        return R.success(staffPerformanceApplicationService.dashboard(staffId));
    }

    @GetMapping("/logs")
    @SaCheckPermission("performance:staff:view")
    @Operation(summary = "绩效流水")
    public R<List<StaffPerformanceLogVO>> logs(@RequestParam Long staffId,
                                               @RequestParam(required = false) String month,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return R.success(staffPerformanceApplicationService.listLogs(staffId, month, page, size));
    }
}

