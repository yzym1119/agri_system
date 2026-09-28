package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.staff.dto.command.RoleAssignCommand;
import com.agri_system.application.staff.dto.command.RoleSaveCommand;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.system.auth.model.SysRole;
import com.agri_system.domain.system.auth.repository.SysStaffRoleRepository;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@Tag(name = "角色管理")
@RequiredArgsConstructor
public class RoleController {

    private final StaffAuthorityService authorityService;
    private final SysStaffRoleRepository staffRoleRepository;

    @GetMapping
    @Operation(summary = "角色列表")
    @SaCheckPermission("role:view")
    public R<List<SysRole>> list() {
        return R.success(authorityService.listRoles());
    }

    @PostMapping
    @Operation(summary = "保存角色")
    @SaCheckPermission("role:save")
    public R<Long> save(@Valid @RequestBody RoleSaveCommand command) {
        return R.success(authorityService.saveRole(command));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑角色")
    @SaCheckPermission("role:save")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveCommand command) {
        authorityService.updateRole(id, command);
        return R.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    @SaCheckPermission("role:delete")
    public R<Void> delete(@PathVariable Long id) {
        // 检查当前登录用户是否拥有该角色，禁止删除自己的角色
        if (StpUtil.isLogin()) {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId.startsWith("STAFF_")) {
                Long currentStaffId = Long.parseLong(loginId.substring("STAFF_".length()));
                List<Long> currentStaffRoleIds = staffRoleRepository.findRoleIdsByStaffId(currentStaffId);
                if (currentStaffRoleIds.contains(id)) {
                    throw new IllegalArgumentException("不能删除自己当前拥有的角色，否则将失去所有权限");
                }
            }
        }
        authorityService.deleteRole(id);
        return R.success();
    }

    @GetMapping("/{id}/menus")
    @Operation(summary = "获取角色已分配的菜单ID列表")
    @SaCheckPermission("role:view")
    public R<List<Long>> getRoleMenus(@PathVariable Long id) {
        return R.success(authorityService.getRoleMenuIds(id));
    }

    @PostMapping("/{id}/menus")
    @Operation(summary = "分配菜单权限")
    @SaCheckPermission("role:assign")
    public R<Void> assignMenus(@PathVariable Long id, @Valid @RequestBody RoleAssignCommand command) {
        // 检查当前登录用户是否拥有该角色，禁止取消自己的所有权限
        if (StpUtil.isLogin()) {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId.startsWith("STAFF_")) {
                Long currentStaffId = Long.parseLong(loginId.substring("STAFF_".length()));
                List<Long> currentStaffRoleIds = staffRoleRepository.findRoleIdsByStaffId(currentStaffId);
                if (currentStaffRoleIds.contains(id) && (command.getMenuIds() == null || command.getMenuIds().isEmpty())) {
                    throw new IllegalArgumentException("不能取消自己当前拥有角色的所有权限，否则将失去所有权限");
                }
            }
        }
        command.setRoleId(id);
        authorityService.assignRoleMenus(command);
        return R.success();
    }

}

