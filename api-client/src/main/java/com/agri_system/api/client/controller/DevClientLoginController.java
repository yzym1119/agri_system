package com.agri_system.api.client.controller;

import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发环境客户登录控制器。
 *
 * <p>安全边界（评审 P0）：该 Controller 通过 {@code auth.dev-login.enabled=true} 显式启用，
 * 生产环境不设置该属性时整个 Bean 不注册，接口在容器层不存在（404），
 * 不再依赖「微信 AppID 是否为空」这种隐式缺省判断。</p>
 *
 * <p>应用层（AuthService.devClientLogin）还有同配置的兜底校验，双重防线。</p>
 */
@RestController
@RequestMapping("/auth/client")
@Tag(name = "开发环境客户登录", description = "仅限显式启用的开发/测试环境")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "auth.dev-login.enabled", havingValue = "true")
public class DevClientLoginController {

    private final AuthService authService;

    /**
     * 开发环境客户登录（需 auth.dev-login.enabled=true）
     */
    @PostMapping("/login/dev")
    @Operation(summary = "开发环境登录", description = "仅当 auth.dev-login.enabled=true 时注册；通过手机号直接登录客户")
    public ResponseEntity<?> devLogin(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 10001, "message", "phone不能为空"));
        }
        LoginResultVO result = authService.devClientLogin(phone);
        return ResponseEntity.ok(result);
    }
}
