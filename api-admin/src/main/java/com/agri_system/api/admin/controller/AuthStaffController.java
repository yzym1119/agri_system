package com.agri_system.api.admin.controller;

import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.dto.StaffLoginCommand;
import com.agri_system.application.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 员工认证控制器
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/auth/staff")
@Tag(name = "员工认证", description = "员工登录、登出、信息查询")
@RequiredArgsConstructor
public class AuthStaffController {
    
    private final AuthService authService;
    
    /**
     * 员工登录
     */
    @PostMapping("/login")
    @Operation(summary = "员工登录", description = "账号密码登录")
    public ResponseEntity<LoginResultVO> login(@Valid @RequestBody StaffLoginCommand command) {
        LoginResultVO result = authService.staffLogin(command);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 员工登出
     */
    @PostMapping("/logout")
    @Operation(summary = "员工登出", description = "登出当前登录的员工")
    public ResponseEntity<Void> logout() {
        authService.staffLogout();
        return ResponseEntity.ok().build();
    }
    
    /**
     * 获取当前登录员工信息
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前登录员工信息", description = "获取当前登录员工的基本信息和权限")
    public ResponseEntity<LoginResultVO.UserInfo> info() {
        LoginResultVO.UserInfo userInfo = authService.getCurrentStaffInfo();
        return ResponseEntity.ok(userInfo);
    }
}


