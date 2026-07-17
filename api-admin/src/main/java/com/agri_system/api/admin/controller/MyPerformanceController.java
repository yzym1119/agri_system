package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.agri_system.application.auth.util.CurrentUser;
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

/**
 * 员工绩效自助接口。
 *
 * <p>普通员工登录后只能查看自己的绩效数据。与 {@link PerformanceStaffController} 的区别是：
 * 这里从当前登录 token 中提取 staffId，禁止指定其他员工 ID，防止越权查询。</p>
 *
 * <p>仅要求登录（{@code @SaCheckLogin}），不绑定特定权限码，让所有员工都能自助查看。</p>
 */
@Tag(name = "MyPerformance", description = "员工绩效自助接口")
@RestController
@RequestMapping("/api/admin/my/performance")
@RequiredArgsConstructor
public class MyPerformanceController {

    private final StaffPerformanceApplicationService staffPerformanceApplicationService;

    @GetMapping("/dashboard")
    @SaCheckLogin
    @Operation(summary = "我的绩效看板")
    public R<StaffDashboardVO> myDashboard() {
        Long staffId = CurrentUser.staffId();
        if (staffId == null) {
            return R.fail(10003, "未检测到员工登录态");
        }
        return R.success(staffPerformanceApplicationService.dashboard(staffId));
    }

    @GetMapping("/logs")
    @SaCheckLogin
    @Operation(summary = "我的绩效流水")
    public R<List<StaffPerformanceLogVO>> myLogs(
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long staffId = CurrentUser.staffId();
        if (staffId == null) {
            return R.fail(10003, "未检测到员工登录态");
        }
        return R.success(staffPerformanceApplicationService.listLogs(staffId, month, page, size));
    }
}
