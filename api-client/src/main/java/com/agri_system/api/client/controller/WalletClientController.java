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

    /** 与 WalletApplicationService 同源的显式渠道开关：未接入 SMS/微信渠道前授权流程整体禁用。 */
    @org.springframework.beans.factory.annotation.Value("${wallet.auth-code.channel.enabled:false}")
    private boolean authCodeChannelEnabled;

    @PostMapping("/send")
    @Operation(summary = "生成并发送钱包验证码", description = "仅允许登录客户为自己申请，且绑定本次计划支付金额；验证码经可信渠道下发，接口不回显原码")
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody WalletAuthCodeSendCommand command) {
        if (!authCodeChannelEnabled) {
            // 无可信发送渠道时明确禁用授权流程，绝不返回"已发送"
            return ResponseEntity.status(503).body(Map.of(
                    "code", "wallet-4003",
                    "message", "验证码发送渠道未接入，钱包授权功能暂不可用"));
        }
        Long customerId = authService.getCurrentCustomerId();
        walletService.generateAndStoreWalletAuthCode(customerId, null, command.getAmount(), 5);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        resp.put("customerId", customerId);
        resp.put("amount", command.getAmount());
        resp.put("message", "验证码已发送，请查收");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/verify")
    @Operation(summary = "校验钱包验证码", description = "校验当前登录客户名下验证码是否仍然有效")
    public ResponseEntity<Map<String, Object>> verify(@Valid @RequestBody WalletAuthCodeVerifyCommand command) {
        if (!authCodeChannelEnabled) {
            // 渠道未接入时不可能存在有效验证码，直接拒绝，保持流程一致
            return ResponseEntity.ok(Map.of("valid", false));
        }
        Long customerId = authService.getCurrentCustomerId();
        boolean valid = walletService.validateWalletAuthCode(customerId, command.getAuthCode());
        return ResponseEntity.ok(Map.of("valid", valid));
    }

}
