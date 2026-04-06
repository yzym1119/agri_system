package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.staff.dto.command.StaffCreateCommand;
import com.agri_system.application.staff.dto.command.StaffResetPasswordCommand;
import com.agri_system.application.staff.dto.command.StaffUpdateCommand;
import com.agri_system.application.staff.dto.query.StaffQuery;
import com.agri_system.application.staff.dto.vo.StaffListVO;
import com.agri_system.application.staff.dto.vo.StaffProfileVO;
import com.agri_system.application.staff.service.StaffLifecycleService;
import com.agri_system.infrastructure.common.response.R;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/staff")
@Tag(name = "员工管理", description = "员工档案、入离职、密码管理")
@RequiredArgsConstructor
public class StaffAdminController {

    private final StaffLifecycleService lifecycleService;

    @PostMapping
    @Operation(summary = "创建员工")
    @SaCheckPermission("staff:create")
    public R<Long> create(@Valid @RequestBody StaffCreateCommand command) {
        return R.success(lifecycleService.create(command));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新员工信息")
    @SaCheckPermission("staff:update")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody StaffUpdateCommand command) {
        command.setId(id);
        lifecycleService.update(command);
        return R.success();
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用员工/离职")
    @SaCheckPermission("staff:disable")
    public R<Void> disable(@PathVariable Long id) {
        lifecycleService.disable(id);
        return R.success();
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用员工")
    @SaCheckPermission("staff:enable")
    public R<Void> enable(@PathVariable Long id) {
        lifecycleService.enable(id);
        return R.success();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "重置员工密码")
    @SaCheckPermission("staff:resetPassword")
    public R<Void> resetPassword(@Valid @RequestBody StaffResetPasswordCommand command) {
        lifecycleService.resetPassword(command.getStaffId(), command.getNewPassword());
        return R.success();
    }

    @GetMapping
    @Operation(summary = "分页查询员工列表")
    @SaCheckPermission("staff:list")
    public R<Page<StaffListVO>> list(@Valid StaffQuery query) {
        return R.success(lifecycleService.queryStaffList(query));
    }

    @GetMapping("/{id}/profile")
    @Operation(summary = "员工档案")
    @SaCheckPermission("staff:view")
    public R<StaffProfileVO> profile(@PathVariable Long id) {
        return R.success(lifecycleService.profile(id));
    }
}

