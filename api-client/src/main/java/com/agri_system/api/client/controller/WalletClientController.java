package com.agri_system.api.client.controller;

import com.agri_system.application.wallet.service.WalletApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/client/public/wallet/auth-code")
@Tag(name = "客户钱包验证码", description = "小程序获取钱包验证码")
@RequiredArgsConstructor
public class WalletClientController {

    private final WalletApplicationService walletService;

    @PostMapping("/send")
    @Operation(summary = "生成并发送钱包验证码（小程序调用）")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        Long customerId = body.get("customerId") == null ? null : Long.valueOf(String.valueOf(body.get("customerId")));
        if (customerId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId required"));
        }
        // For security and consistency, the send API does not accept orderId/amount;
        // order association and amount are recorded when the code is consumed during order creation.
        String code = walletService.generateAndStoreWalletAuthCode(customerId, null, null, 5);
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "ok");
        // For production you would NOT return the code; return for testing/dev only
        resp.put("authCode", code);
        resp.put("message", "验证码已生成并记录（实际请通过微信/短信下发）");
        return ResponseEntity.ok(resp);
    }

}


