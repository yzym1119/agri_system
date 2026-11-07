package com.agri_system.api.client.controller;

import com.agri_system.application.auth.service.AuthService;
import com.agri_system.application.wallet.dto.WalletAuthCodeSendCommand;
import com.agri_system.application.wallet.dto.WalletAuthCodeVerifyCommand;
import com.agri_system.application.wallet.service.WalletApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/client/wallet/auth-code")
@Tag(name = "客户钱包验证码", description = "登录客户获取钱包验证码")
@RequiredArgsConstructor
public class WalletClientController {

    private final WalletApplicationService walletService;
    private final AuthService authService;

    @PostMapping("/send")
    @Operation(summary = "生成并发送钱包验证码", description = "仅允许登录客户为自己申请，且绑定本次计划支付金额；验证码经可信渠道下发，接口不回显原码")
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody WalletAuthCodeSendCommand command) {
        Long customerId = authService.getCurrentCustomerId();
        walletService.generateAndStoreWalletAuthCode(customerId, null, command.getAmount(), 5);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("customerId", customerId);
        resp.put("amount", command.getAmount());
        resp.put("message", "验证码已生成，请查收（经可信渠道下发）");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify")
    @Operation(summary = "校验钱包验证码", description = "校验当前登录客户名下验证码是否仍然有效")
    public ResponseEntity<Map<String, Object>> verify(@Valid @RequestBody WalletAuthCodeVerifyCommand command) {
        Long customerId = authService.getCurrentCustomerId();
        boolean valid = walletService.validateWalletAuthCode(customerId, command.getAuthCode());
        return ResponseEntity.ok(Map.of("valid", valid));
    }

}
