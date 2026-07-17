package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.staff.dto.vo.MenuTreeVO;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/menus")
@Tag(name = "菜单管理")
@RequiredArgsConstructor
public class MenuController {

    private final StaffAuthorityService authorityService;

    @GetMapping("/tree")
    @Operation(summary = "菜单树")
    @SaCheckPermission("menu:view")
    public R<List<MenuTreeVO>> tree() {
        return R.success(authorityService.getMenuTree());
    }

    @GetMapping("/current")
    @SaCheckLogin
    @Operation(summary = "当前员工可见菜单树")
    public R<List<MenuTreeVO>> current() {
        return R.success(authorityService.getCurrentMenuTree(getCurrentStaffId()));
    }

    private Long getCurrentStaffId() {
        if (!StpUtil.isLogin()) {
            throw new IllegalArgumentException("未登录");
        }
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId != null && loginId.startsWith("STAFF_")) {
            return Long.parseLong(loginId.substring("STAFF_".length()));
        }
        throw new IllegalArgumentException("当前登录用户不是员工");
    }
}
