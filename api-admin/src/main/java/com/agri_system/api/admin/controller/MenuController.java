package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
}

