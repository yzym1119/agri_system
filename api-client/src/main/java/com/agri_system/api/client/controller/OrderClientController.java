package com.agri_system.api.client.controller;

import com.agri_system.application.order.dto.command.OrderCreateCommand;
import com.agri_system.application.order.dto.command.OrderPayCommand;
import com.agri_system.application.order.dto.vo.OrderDetailVO;
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
    
    /**
     * 创建订单
     */
    @PostMapping
    @Operation(summary = "创建订单", description = "小程序创建订单，状态为待支付")
    public ResponseEntity<Long> createOrder(@Valid @RequestBody OrderCreateCommand command,
                                           @RequestHeader("X-Customer-Id") Long customerId) {
        Long orderId = orderService.createOrder(command, customerId);
        return ResponseEntity.ok(orderId);
    }
    
    /**
     * 订单支付
     */
    @PostMapping("/{orderId}/pay")
    @Operation(summary = "订单支付", description = "小程序订单支付，支持混合支付")
    public ResponseEntity<OrderPayPreviewVO> payOrder(@PathVariable Long orderId,
                                                      @Valid @RequestBody OrderPayCommand command,
                                                      @RequestHeader("X-Customer-Id") Long customerId) {
        command.setOrderId(orderId);
        OrderPayPreviewVO preview = orderService.payOrder(command, customerId);
        return ResponseEntity.ok(preview);
    }
    
    /**
     * 查询订单详情
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
    public ResponseEntity<OrderDetailVO> getOrderDetail(@PathVariable Long orderId,
                                                         @RequestHeader("X-Customer-Id") Long customerId) {
        OrderDetailVO detail = orderService.getOrderDetail(orderId);
        // TODO: 校验订单是否属于该客户
        return ResponseEntity.ok(detail);
    }
    
    /**
     * 确认收货
     */
    @PostMapping("/{orderId}/confirm-receipt")
    @Operation(summary = "确认收货", description = "小程序订单确认收货")
    public ResponseEntity<Void> confirmReceipt(@PathVariable Long orderId,
                                               @RequestHeader("X-Customer-Id") Long customerId) {
        // TODO: 实现确认收货逻辑
        return ResponseEntity.ok().build();
    }
}








