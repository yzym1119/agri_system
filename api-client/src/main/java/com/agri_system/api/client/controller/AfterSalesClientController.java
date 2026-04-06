package com.agri_system.api.client.controller;

import com.agri_system.application.after_sales.dto.command.ClientApplyCommand;
import com.agri_system.application.after_sales.service.AfterSalesApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 售后（小程序端）
 */
@RestController
@RequestMapping("/api/client/after-sales")
@RequiredArgsConstructor
@Tag(name = "售后管理（客户端）", description = "小程序售后申请与撤销")
public class AfterSalesClientController {

    private final AfterSalesApplicationService afterSalesService;

    @PostMapping("/apply")
    @Operation(summary = "申请售后", description = "仅支持待发货/待自提订单拦截取消")
    public ResponseEntity<Long> apply(@Valid @RequestBody ClientApplyCommand command,
                                      @RequestHeader("X-Customer-Id") Long customerId) {
        Long afterSalesId = afterSalesService.applyFromClient(command, customerId);
        return ResponseEntity.ok(afterSalesId);
    }

    @PostMapping("/{afterSalesId}/cancel")
    @Operation(summary = "撤销售后", description = "待审核状态下可撤销")
    public ResponseEntity<Void> cancel(@PathVariable Long afterSalesId,
                                       @RequestHeader("X-Customer-Id") Long customerId) {
        afterSalesService.cancel(afterSalesId, customerId);
        return ResponseEntity.ok().build();
    }
}








