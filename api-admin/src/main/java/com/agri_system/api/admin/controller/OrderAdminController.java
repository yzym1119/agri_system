package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.order.dto.command.OrderPickupVerifyCommand;
import com.agri_system.application.order.dto.command.OrderRefundCommand;
import com.agri_system.application.order.dto.command.OrderShipCommand;
import com.agri_system.application.order.dto.command.POSOrderCreateCommand;
import com.agri_system.application.order.dto.command.PickupFulfillmentCommand;
import com.agri_system.application.order.dto.command.PickupRequestCommand;
import com.agri_system.application.order.dto.query.OrderPageQuery;
import com.agri_system.application.order.dto.vo.OrderDetailVO;
import com.agri_system.application.order.dto.vo.OrderPageVO;
import com.agri_system.application.order.dto.vo.PickupWorkItemVO;
import com.agri_system.application.order.service.OrderApplicationService;
import com.agri_system.application.order.service.PickupFulfillmentService;
import com.agri_system.domain.order.enums.OrderMainPaymentMethod;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final OrderApplicationService orderService;
    private final PickupFulfillmentService pickupFulfillmentService;
    private final com.agri_system.application.order.service.PosSaleSubmissionService posSubmissions;
    
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
        // 幂等协调器先锁原请求；钱包验证码由原钱货事务内的条件UPDATE一次性核销。
        return ResponseEntity.ok(posSubmissions.submit(command.getRequestKey(), command, currentPosStaff()));
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
    @PostMapping("/{orderId}/refund-preview")
    @SaCheckPermission(value={"order:refund","afterSales:refund"},mode=cn.dev33.satoken.annotation.SaMode.OR)
    public ResponseEntity<com.agri_system.application.order.dto.vo.OrderRefundPreviewVO> previewRefund(@PathVariable Long orderId,
            @Valid @RequestBody(required=false) com.agri_system.application.order.dto.command.OrderRefundPreviewCommand command) {
        return ResponseEntity.ok(orderService.previewRefund(orderId,command));
    }

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
    public ResponseEntity<Void> confirmReceipt(@PathVariable Long orderId,
            @RequestBody(required = false) ReceiptConfirmation request) {
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
        // 保留旧客户端无请求体及空对象调用；新版页面总是提交核对摘要。
        if (request == null || request.confirmationKey() == null) orderService.confirmReceipt(orderId, staffId);
        else {
            if (request.confirmationKey().isBlank()) {
                throw new IllegalArgumentException("请先核对配送商品与收货信息");
            }
            orderService.confirmReceipt(orderId, staffId, request.confirmationKey());
        }
        return ResponseEntity.ok().build();
    }

    public record ReceiptConfirmation(String confirmationKey) {}

    @GetMapping("/pos/submissions/{requestKey}")
    @SaCheckPermission("pos:order:create")
    public com.agri_system.application.order.service.PosSaleSubmissionService.Result posSubmission(@PathVariable String requestKey) {
        return posSubmissions.lookup(requestKey, currentPosStaff());
    }
    @PostMapping("/pos/submissions/{requestKey}/cancel")
    @SaCheckPermission("pos:order:create")
    public com.agri_system.application.order.service.PosSaleSubmissionService.Result cancelPosSubmission(@PathVariable String requestKey) {
        return posSubmissions.cancel(requestKey, currentPosStaff());
    }
    private Long currentPosStaff() {
        String id=StpUtil.getLoginIdAsString();
        if(id==null || !id.matches("STAFF_[0-9]+")) throw new IllegalArgumentException("请使用员工身份核对收银请求");
        return Long.valueOf(id.substring(6));
    }


    /**
     * 查询门店取货工作项（零售事实链 Task9：管理端取货待办页）
     */
    @GetMapping("/pickup/requests")
    @Operation(summary = "查询取货待办工作项", description = "按门店查询取货请求（status 缺省为 PENDING），含订单号/商品/货位标签")
    @SaCheckPermission("order:pickup:verify")
    public ResponseEntity<Map<String, Object>> listPickupRequests(@RequestParam Long storeId,
                                                                  @RequestParam(required = false) String status) {
        List<PickupWorkItemVO> items = pickupFulfillmentService.listPickupRequests(storeId, status);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", items);
        return ResponseEntity.ok(result);
    }

    /**
     * 创建库房取货请求（零售事实链 Task5：货架缺货或选择库房取货时登记后仓拣货）
     */
    @PostMapping("/{orderId}/pickup/requests")
    @Operation(summary = "创建库房取货请求", description = "为订单明细登记库房（后仓）拣货请求，返回取货请求ID")
    @SaCheckPermission("order:pickup:verify")
    public ResponseEntity<Long> createPickupRequest(@PathVariable Long orderId,
                                                    @Valid @RequestBody PickupRequestCommand command) {
        command.setOrderId(orderId);
        Long requestId = pickupFulfillmentService.createRequest(
                orderId, command.getOrderItemId(), command.getSourceLocationId());
        return ResponseEntity.ok(requestId);
    }

    /**
     * 完成库房取货（零售事实链 Task5：扫码或显式确认批次与货位，产生批次成本证据）
     */
    @PostMapping("/pickup/requests/{requestId}/fulfill")
    @Operation(summary = "完成库房取货", description = "扫描内部批次标签或显式确认批次与货位，回填实际批次与成本证据")
    @SaCheckPermission("order:pickup:verify")
    public ResponseEntity<Void> fulfillPickupRequest(@PathVariable Long requestId,
                                                     @Valid @RequestBody PickupFulfillmentCommand command) {
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
        pickupFulfillmentService.fulfill(requestId, command, staffId);
        return ResponseEntity.ok().build();
    }
}





