package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.common.PageResult;
import com.agri_system.application.points.dto.*;
import com.agri_system.application.points.service.PointsFulfillmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/points/orders")
@Tag(name = "积分兑换单管理")
@RequiredArgsConstructor
public class PointsOrderAdminController {

    private final PointsFulfillmentService fulfillmentService;

    @GetMapping
    @SaCheckPermission("marketing:points:order:query")
    @Operation(summary = "积分兑换单列表")
    public ResponseEntity<PageResult<PointsExchangeOrderVO>> list(@Valid PointsOrderQuery query) {
        return ResponseEntity.ok(fulfillmentService.listOrders(query));
    }

    @PostMapping("/audit")
    @SaCheckPermission("marketing:points:order:audit")
    @Operation(summary = "审核积分兑换单")
    public ResponseEntity<Void> audit(@Valid @RequestBody PointsAuditCommand command) {
        fulfillmentService.audit(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/notify")
    @SaCheckPermission("marketing:points:order:audit")
    @Operation(summary = "通知客户取货")
    public ResponseEntity<Void> notifyPickup(@Valid @RequestBody PointsNotifyCommand command) {
        fulfillmentService.notifyPickup(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify")
    @SaCheckPermission("marketing:points:order:verify")
    @Operation(summary = "核销积分兑换单")
    public ResponseEntity<PointsExchangeOrderVO> verify(@Valid @RequestBody PointsVerifyCommand command) {
        return ResponseEntity.ok(fulfillmentService.verify(command));
    }
}

