package com.agri_system.api.client.controller;

import com.agri_system.application.auth.dto.ClientWechatLoginCommand;
import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户认证控制器
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/auth/client")
@Tag(name = "客户认证", description = "客户微信登录")
@RequiredArgsConstructor
public class AuthClientController {
    
    private final AuthService authService;
    
    /**
     * 客户微信登录
     */
    @PostMapping("/login/wechat")
    @Operation(summary = "客户微信登录", description = "通过微信code登录，自动注册新用户")
    public ResponseEntity<LoginResultVO> wechatLogin(@Valid @RequestBody ClientWechatLoginCommand command) {
        LoginResultVO result = authService.clientWechatLogin(command);
        return ResponseEntity.ok(result);
    }
    
    /**
     * 客户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "客户登出", description = "登出当前登录的客户")
    public ResponseEntity<Void> logout() {
        authService.clientLogout();
        return ResponseEntity.ok().build();
    }

    // 开发环境登录已拆分到 DevClientLoginController，
    // 通过 auth.dev-login.enabled=true 显式启用，生产环境不注册该 Bean。
}






