package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.order.dto.command.OrderPickupVerifyCommand;
import com.agri_system.application.order.dto.command.OrderRefundCommand;
import com.agri_system.application.order.dto.command.OrderShipCommand;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.dto.vo.OrderDetailVO;
import com.agri_system.application.order.dto.vo.OrderPageVO;
import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.infrastructure.common.util.RedisDistributedLock;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理端控制器
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Tag(name = "订单管理（管理端）", description = "订单管理相关接口")
public class OrderAdminController {

    private static final Logger logger = LoggerFactory.getLogger(OrderAdminController.class);

    private final OrderApplicationService orderService;
    private final RedisDistributedLock redisLock;
    
    /**
     * 分页查询订单列表
     */
    @GetMapping
    @Operation(summary = "分页查询订单列表", description = "支持订单号/手机号/收货人关键词搜索，门店、订单来源、配送方式、状态、时间范围筛选")
    @SaCheckPermission("order:query")
    public ResponseEntity<Page<OrderPageVO>> queryOrderPage(@Valid OrderPageQuery query) {
        Page<OrderPageVO> page = orderService.queryOrderPage(query);
        return ResponseEntity.ok(page);
    }
    
    /**
     * POS创建订单（记账模式）
     */
    @PostMapping("/pos")
    @Operation(summary = "POS创建订单", description = "POS开单，支持外部支付（记账）和内部支付（余额/专用金）")
    @SaCheckPermission("pos:order:create")
    public ResponseEntity<Long> posCreateOrder(@Valid @RequestBody POSOrderCreateCommand command) {
        // Redis分布式锁：防止并发请求处理同一个验证码
        String lockKey = null;
        String requestId = java.util.UUID.randomUUID().toString();

        // 如果使用钱包验证码或 payments 列表中包含余额/专用金，获取分布式锁
        boolean needLock = false;
        String authCodeForLock = null;
        if (StringUtils.hasText(command.getWalletAuthCode())) {
            needLock = true;
            authCodeForLock = command.getWalletAuthCode();
        } else if (command.getPayments() != null && !command.getPayments().isEmpty()) {
            for (var p : command.getPayments()) {
                if (p != null && (p.getPaymentType() != null) &&
                        (p.getPaymentType().equals(3) || p.getPaymentType().equals(4))) { // 3=余额,4=专用金
                    needLock = true;
                    authCodeForLock = p.getAuthCode();
                    break;
                }
            }
        }

        if (needLock && StringUtils.hasText(authCodeForLock)) {
            lockKey = "lock:pos:auth_code:" + authCodeForLock;
            boolean lockAcquired = redisLock.tryLock(lockKey, requestId, 60); // 60秒锁过期时间，避免超时
            if (!lockAcquired) {
                throw new IllegalArgumentException("正在处理中，请勿重复提交");
            }
            logger.info("Acquired distributed lock for auth code: {} with requestId: {}", authCodeForLock, requestId);
        }

        try {
            // 从 Sa-Token 获取当前登录员工 ID（loginId 形如 STAFF_123）
            String loginId = StpUtil.getLoginIdAsString();
            Long staffId = null;
            if (loginId != null && loginId.startsWith("STAFF_")) {
                try {
                    staffId = Long.parseLong(loginId.substring("STAFF_".length()));
                } catch (NumberFormatException e) {
                    // 解析失败时保持 null，由下游根据需要处理
                }
            }

            Long orderId = orderService.createPOSOrder(command, staffId);
            return ResponseEntity.ok(orderId);
        } finally {
            // 释放分布式锁
            if (lockKey != null) {
                boolean released = redisLock.releaseLock(lockKey, requestId);
                if (released) {
                    logger.info("Released distributed lock for auth code: {} with requestId: {}", command.getWalletAuthCode(), requestId);
                } else {
                    logger.warn("Failed to release distributed lock for auth code: {} with requestId: {}", command.getWalletAuthCode(), requestId);
                }
            }
        }
    }
    
    /**
     * 自提核销
     */
    @PostMapping("/pickup/verify")
    @Operation(summary = "自提核销", description = "根据自提码核销订单")
    @SaCheckPermission("order:pickup:verify")
    public ResponseEntity<Void> verifyPickup(@Valid @RequestBody OrderPickupVerifyCommand command) {
        // 从 Sa-Token 获取当前登录员工 ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 解析失败时保持 null
            }
        }
        orderService.verifyPickup(command, staffId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 发货
     */
    @PostMapping("/{orderId}/ship")
    @Operation(summary = "发货", description = "订单发货，填写物流信息")
    @SaCheckPermission("order:ship")
    public ResponseEntity<Void> shipOrder(@PathVariable Long orderId,
                                          @Valid @RequestBody OrderShipCommand command) {
        // 从 Sa-Token 获取当前登录员工 ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 解析失败时保持 null
            }
        }
        command.setOrderId(orderId);
        orderService.shipOrder(command, staffId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 退款
     */
    @PostMapping("/{orderId}/refund")
    @Operation(summary = "退款", description = "订单退款，按LIFO顺序退回")
    @SaCheckPermission("order:refund")
    public ResponseEntity<Void> refundOrder(@PathVariable Long orderId,
                                           @Valid @RequestBody OrderRefundCommand command) {
        // 从 Sa-Token 获取当前登录员工 ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 解析失败时保持 null
            }
        }
        command.setOrderId(orderId);
        orderService.refundOrder(command, staffId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 查询订单详情
     */
    @GetMapping("/{orderId}")
    @Operation(summary = "查询订单详情", description = "根据订单ID查询订单详情")
    @SaCheckPermission("order:query")
    public ResponseEntity<OrderDetailVO> getOrderDetail(@PathVariable Long orderId) {
        OrderDetailVO detail = orderService.getOrderDetail(orderId);
        return ResponseEntity.ok(detail);
    }
    
    /**
     * 确认收货（管理员强制确认）
     */
    @PostMapping("/{orderId}/confirm-receipt")
    @Operation(summary = "确认收货", description = "管理员强制确认订单收货，将已发货订单标记为已完成")
    @SaCheckPermission("order:ship")
    public ResponseEntity<Void> confirmReceipt(@PathVariable Long orderId) {
        // 从 Sa-Token 获取当前登录员工 ID
        String loginId = StpUtil.getLoginIdAsString();
        Long staffId = null;
        if (loginId != null && loginId.startsWith("STAFF_")) {
            try {
                staffId = Long.parseLong(loginId.substring("STAFF_".length()));
            } catch (NumberFormatException e) {
                // 解析失败时保持 null
            }
        }
        orderService.confirmReceipt(orderId, staffId);
        return ResponseEntity.ok().build();
    }
}







