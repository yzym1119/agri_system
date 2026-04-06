package com.agri_system.api.admin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.after_sales.service.AfterSalesApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 售后管理端接口
 */
@RestController
@RequestMapping("/api/admin/after-sales")
@RequiredArgsConstructor
@Tag(name = "售后管理（管理端）")
public class AfterSalesAdminController {

    private final AfterSalesApplicationService afterSalesService;

    @PostMapping
    @Operation(summary = "创建退货售后", description = "仅支持线下退货退款，自动审核并回补库存")
    public ResponseEntity<Long> create(@Valid @RequestBody AdminReturnCommand command) {
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
        Long afterSalesId = afterSalesService.createFromAdmin(command, staffId);
        return ResponseEntity.ok(afterSalesId);
    }

    @PostMapping("/{afterSalesId}/audit")
    @Operation(summary = "审核售后", description = "小程序售后审核，驳回或进入待退款")
    public ResponseEntity<Void> audit(@PathVariable Long afterSalesId,
                                      @Valid @RequestBody AfterSalesAuditCommand command) {
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
        command.setAfterSalesId(afterSalesId);
        afterSalesService.audit(command, staffId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{afterSalesId}/refund")
    @Operation(summary = "确认退款", description = "确认退款后调用订单模块进行原路退款")
    public ResponseEntity<Void> refund(@PathVariable Long afterSalesId,
                                       @Valid @RequestBody AfterSalesRefundCommand command) {
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
        command.setAfterSalesId(afterSalesId);
        afterSalesService.refund(command, staffId);
        return ResponseEntity.ok().build();
    }
}








