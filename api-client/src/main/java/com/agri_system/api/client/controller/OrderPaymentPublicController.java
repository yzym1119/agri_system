package com.agri_system.api.client.controller;

import com.agri_system.application.order.dto.command.OrderExternalPaymentCallbackCommand;
import com.agri_system.application.order.service.OrderApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单支付公开回调控制器
 *
 * 安全说明：
 * - 该接口不要求登录，但必须通过 shared secret 验证调用方身份
 * - 生产环境通过 PAYMENT_CALLBACK_SECRET 环境变量设置密钥
 * - 未配置密钥时，接口将被禁用（返回 503）
 */
@RestController
@RequestMapping("/api/public/orders/payments")
@RequiredArgsConstructor
@Tag(name = "订单支付回调（公开）", description = "外部支付平台或网关调用的公开确认接口")
public class OrderPaymentPublicController {

    private final OrderApplicationService orderService;

    @Value("${payment.callback.secret:}")
    private String callbackSecret;

    @PostMapping("/confirm")
    @Operation(summary = "确认外部支付回调", description = "根据支付意图号和外部流水号确认支付。需在请求头 X-Payment-Secret 中传递密钥")
    public ResponseEntity<String> confirm(
            @RequestHeader(value = "X-Payment-Secret", required = false) String secret,
            @Valid @RequestBody OrderExternalPaymentCallbackCommand command) {

        // 安全：未配置密钥时禁用接口
        if (callbackSecret == null || callbackSecret.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Payment callback not configured");
        }

        // 安全：验证密钥
        if (!callbackSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid payment secret");
        }

        orderService.confirmExternalPaymentCallback(command);
        return ResponseEntity.ok("success");
    }
}
