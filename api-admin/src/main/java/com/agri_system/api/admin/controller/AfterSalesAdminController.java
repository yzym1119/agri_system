package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.after_sales.dto.command.AdminReturnCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesAuditCommand;
import com.agri_system.application.after_sales.dto.command.AfterSalesRefundCommand;
import com.agri_system.application.after_sales.dto.query.AfterSalesPageQuery;
import com.agri_system.application.after_sales.dto.vo.AfterSalesDetailVO;
import com.agri_system.application.after_sales.dto.vo.AfterSalesPageVO;
import com.agri_system.application.after_sales.service.AfterSalesApplicationService;
import com.mybatisflex.core.paginate.Page;
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

    @GetMapping
    @Operation(summary = "分页查询售后单", description = "支持售后单号、订单号、客户名/手机号、门店、类型、状态、时间范围筛选")
    @SaCheckPermission("afterSales:query")
    public ResponseEntity<Page<AfterSalesPageVO>> list(@Valid AfterSalesPageQuery query) {
        return ResponseEntity.ok(afterSalesService.queryPage(query));
    }

    @GetMapping("/{afterSalesId}")
    @Operation(summary = "查询售后详情", description = "根据售后单ID查询售后主单和明细")
    @SaCheckPermission("afterSales:query")
    public ResponseEntity<AfterSalesDetailVO> detail(@PathVariable Long afterSalesId) {
        return ResponseEntity.ok(afterSalesService.getDetail(afterSalesId));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "按订单查询售后记录", description = "用于订单上下文查看关联售后记录")
    @SaCheckPermission("order:query")
    public ResponseEntity<java.util.List<AfterSalesDetailVO>> listByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(afterSalesService.getByOrderId(orderId));
    }

    @PostMapping
    @Operation(summary = "创建退货售后", description = "仅支持线下退货退款，自动审核并回补库存")
    @SaCheckPermission("order:refund")
    public ResponseEntity<Long> create(@Valid @RequestBody AdminReturnCommand command) {
        return ResponseEntity.ok(afterSalesService.createFromAdmin(command, resolveCurrentStaffId()));
    }

    @PostMapping("/{afterSalesId}/audit")
    @Operation(summary = "审核售后", description = "小程序售后审核，驳回或进入待退款")
    @SaCheckPermission("afterSales:audit")
    public ResponseEntity<Void> audit(@PathVariable Long afterSalesId,
                                      @Valid @RequestBody AfterSalesAuditCommand command) {
        command.setAfterSalesId(afterSalesId);
        afterSalesService.audit(command, resolveCurrentStaffId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{afterSalesId}/refund")
    @Operation(summary = "确认退款", description = "确认退款后调用订单模块进行原路退款")
    @SaCheckPermission("afterSales:refund")
    public ResponseEntity<Void> refund(@PathVariable Long afterSalesId,
                                       @Valid @RequestBody AfterSalesRefundCommand command) {
        command.setAfterSalesId(afterSalesId);
        afterSalesService.refund(command, resolveCurrentStaffId());
        return ResponseEntity.ok().build();
    }

    private Long resolveCurrentStaffId() {
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId == null || !loginId.startsWith("STAFF_")) {
            return null;
        }
        try {
            return Long.parseLong(loginId.substring("STAFF_".length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}







