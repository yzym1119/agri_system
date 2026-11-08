package com.agri_system.api.client.controller;

import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.application.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开发环境客户登录控制器。
 *
 * <p>安全边界（评审 P0/P2，双硬约束）：同时满足以下两个条件该 Controller 才注册——</p>
 * <ol>
 *   <li>{@code @Profile("!prod")}：Spring 环境 profile 含 prod 时硬禁用，
 *       即使运维误配 {@code auth.dev-login.enabled=true} 也无法在生产注册；</li>
 *   <li>{@code auth.dev-login.enabled=true}：非 prod 环境也必须显式开启，杜绝隐式缺省。</li>
 * </ol>
 *
 * <p>应用层（AuthService.devClientLogin）还有同两条规则的兜底校验（prod 硬拒 + 配置开关），
 * 即使端点被误注册，调用也会被拒绝——三重防线。</p>
 */
@RestController
@RequestMapping("/auth/client")
@Tag(name = "开发环境客户登录", description = "仅限显式启用的开发/测试环境，prod 环境硬禁用")
@RequiredArgsConstructor
@Profile("!prod")
@ConditionalOnProperty(name = "auth.dev-login.enabled", havingValue = "true")
public class DevClientLoginController {

    private final AuthService authService;

    /**
     * 开发环境客户登录（需非 prod profile 且 auth.dev-login.enabled=true）
     */
    @PostMapping("/login/dev")
    @Operation(summary = "开发环境登录", description = "仅当非 prod 且 auth.dev-login.enabled=true 时注册；通过手机号直接登录客户")
    public ResponseEntity<?> devLogin(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 10001, "message", "phone不能为空"));
        }
        LoginResultVO result = authService.devClientLogin(phone);
        return ResponseEntity.ok(result);
    }
}
