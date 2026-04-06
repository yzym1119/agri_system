package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.performance.dto.*;
import com.agri_system.application.performance.service.PerformanceApplicationService;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "PerformanceAdmin", description = "绩效规则与工资单接口")
@RestController
@RequestMapping("/api/admin/performance")
@RequiredArgsConstructor
public class PerformanceAdminController {

    private final PerformanceApplicationService performanceApplicationService;

    @PostMapping("/rule")
    @SaCheckPermission("performance:rule")
    @Operation(summary = "创建/更新提成规则")
    public R<Long> createRule(@Valid @RequestBody CommissionRuleCreateCommand command) {
        return R.success(performanceApplicationService.createOrUpdateRule(command));
    }

    @GetMapping("/rule")
    @SaCheckPermission("performance:rule")
    @Operation(summary = "查询提成规则")
    public R<List<CommissionRuleVO>> listRules(CommissionRuleQuery query) {
        return R.success(performanceApplicationService.listRules(query));
    }

    @PostMapping("/goal")
    @SaCheckPermission("performance:goal:set")
    @Operation(summary = "设置员工月目标")
    public R<Void> setGoal(@Valid @RequestBody StaffMonthlyGoalCommand command) {
        performanceApplicationService.setMonthlyGoal(command);
        return R.success();
    }

    @GetMapping("/bill")
    @SaCheckPermission("performance:salary:view")
    @Operation(summary = "查询工资单")
    public R<List<StaffSalaryBillVO>> listBills(@RequestParam(required = false) String month,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return R.success(performanceApplicationService.listSalaryBills(month, status, page, size));
    }

    @PostMapping("/bill/{id}/confirm")
    @SaCheckPermission("performance:salary:confirm")
    @Operation(summary = "确认工资单")
    public R<Void> confirmBill(@PathVariable Long id) {
        performanceApplicationService.confirmSalaryBill(id);
        return R.success();
    }
}

