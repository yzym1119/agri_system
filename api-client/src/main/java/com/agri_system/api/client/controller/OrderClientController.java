package com.agri_system.api.client.controller;

import com.agri_system.application.auth.service.AuthService;
import com.agri_system.application.order.dto.command.OrderCreateCommand;
import com.agri_system.application.order.dto.command.OrderExternalPaymentConfirmCommand;
import com.agri_system.application.order.dto.command.OrderPayCommand;
import com.agri_system.application.order.dto.vo.OrderDetailVO;
import com.agri_system.application.order.dto.vo.OrderExternalPaymentIntentVO;
import com.agri_system.application.order.dto.vo.OrderPayPreviewVO;
import com.agri_system.application.order.service.OrderApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 订单客户端控制器（小程序）
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/client/orders")
@RequiredArgsConstructor
@Tag(name = "订单管理（客户端）", description = "小程序订单相关接口")
public class OrderClientController {
    
    private final OrderApplicationService orderService;
    private final AuthService authService;
    
    /**
     * 创建订单
     */
    @PostMapping
    @Operation(summary = "创建订单", description = "小程序创建订单，状态为待支付")
    public ResponseEntity<Long> createOrder(@Valid @RequestBody OrderCreateCommand command) {
        Long customerId = authService.getCurrentCustomerId();
        Long orderId = orderService.createOrder(command, customerId);
        return ResponseEntity.ok(orderId);
    }
    
    /**
     * 订单支付预览
     */
    @PostMapping("/{orderId}/payment-preview")
    @Operation(summary = "订单支付预览", description = "返回当前订单在优惠券、余额、专用金组合下的支付拆分结果")
    public ResponseEntity<OrderPayPreviewVO> previewPayment(@PathVariable Long orderId,
                                                            @Valid @RequestBody OrderPayCommand command) {
        Long customerId = authService.getCurrentCustomerId();
        command.setOrderId(orderId);
        OrderPayPreviewVO preview = orderService.previewOrderPayment(command, customerId);
        return ResponseEntity.ok(preview);
    }

    /**
     * 订单支付
     */
    @PostMapping("/{orderId}/pay")
    @Operation(summary = "订单支付", description = "兼容入口。若无需外部支付则直接完成支付；若仍需微信/支付宝则仅返回预览")
    public ResponseEntity<OrderPayPreviewVO> payOrder(@PathVariable Long orderId,
                                                      @Valid @RequestBody OrderPayCommand command) {
        Long customerId = authService.getCurrentCustomerId();
        command.setOrderId(orderId);
        OrderPayPreviewVO preview = orderService.payOrder(command, customerId);
        return ResponseEntity.ok(preview);
    }

    /**
     * 创建外部支付发起信息
     */
    @PostMapping("/{orderId}/payment-intent")
    @Operation(summary = "创建外部支付发起信息", description = "当订单仍需微信/支付宝支付时，返回支付金额、订单号和预览信息")
    public ResponseEntity<OrderExternalPaymentIntentVO> createPaymentIntent(@PathVariable Long orderId,
                                                                            @Valid @RequestBody OrderPayCommand command) {
        Long customerId = authService.getCurrentCustomerId();
        command.setOrderId(orderId);
        OrderExternalPaymentIntentVO intent = orderService.createExternalPaymentIntent(command, customerId);
        return ResponseEntity.ok(intent);
    }

    /**
     * 确认外部支付
     */
    @PostMapping("/{orderId}/payment-confirmation")
    @Operation(summary = "确认外部支付", description = "外部支付成功后，携带 payment-intent 返回的意图号和支付流水确认，才会真正完成订单支付并扣库存")
    public ResponseEntity<Void> confirmExternalPayment(@PathVariable Long orderId,
                                                       @Valid @RequestBody OrderExternalPaymentConfirmCommand command) {
        Long customerId = authService.getCurrentCustomerId();
        orderService.confirmExternalPayment(command, customerId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 查询订单详情
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
    public ResponseEntity<OrderDetailVO> getOrderDetail(@PathVariable Long orderId) {
        Long customerId = authService.getCurrentCustomerId();
        OrderDetailVO detail = orderService.getOrderDetailForCustomer(orderId, customerId);
        return ResponseEntity.ok(detail);
    }
    
    /**
     * 确认收货
     */
    @PostMapping("/{orderId}/confirm-receipt")
    @Operation(summary = "确认收货", description = "小程序订单确认收货")
    public ResponseEntity<Void> confirmReceipt(@PathVariable Long orderId) {
        Long customerId = authService.getCurrentCustomerId();
        orderService.confirmReceiptForCustomer(orderId, customerId);
        return ResponseEntity.ok().build();
    }
}




