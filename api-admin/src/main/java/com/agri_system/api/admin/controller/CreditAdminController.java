package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.credit.dto.*;
import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.credit.service.CreditRepaymentService;
import com.agri_system.domain.credit.model.CreditRepaymentLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/credit")
@Tag(name = "赊销管理", description = "赊销台账、还款、对账单接口")
@RequiredArgsConstructor
public class CreditAdminController {

    private final CreditApplicationService creditApplicationService;
    private final CreditRepaymentService creditRepaymentService;

    @PostMapping("/repay")
    @SaCheckPermission("credit:repay")
    @Operation(summary = "录入还款并执行核销")
    public ResponseEntity<CreditRepaymentLog> repay(@Valid @RequestBody CreditRepayCommand command) {
        return ResponseEntity.ok(creditRepaymentService.repay(command));
    }

    @GetMapping("/accounts")
    @SaCheckPermission("credit:account:view")
    @Operation(summary = "查询欠款单据")
    public ResponseEntity<com.mybatisflex.core.paginate.Page<CreditAccountVO>> listAccounts(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(creditApplicationService.listAccountsPage(customerId, status, pageNum, pageSize));
    }

    @GetMapping("/repayments")
    @SaCheckPermission("credit:repayment:view")
    @Operation(summary = "查询还款记录")
    public ResponseEntity<List<CreditRepaymentVO>> listRepayments(@RequestParam Long customerId) {
        return ResponseEntity.ok(creditApplicationService.listRepayments(customerId));
    }

    @GetMapping("/statistics")
    @SaCheckPermission("credit:statistics:view")
    @Operation(summary = "查询客户赊销统计")
    public ResponseEntity<CreditDebtVO> statistics(@RequestParam Long customerId) {
        return ResponseEntity.ok(creditApplicationService.statistics(customerId));
    }

    @PutMapping("/limit")
    @SaCheckPermission("credit:limit:update")
    @Operation(summary = "调整客户赊销额度")
    public ResponseEntity<Void> updateLimit(@RequestParam Long customerId,
                                            @RequestParam BigDecimal creditLimit) {
        creditApplicationService.updateCreditLimit(customerId, creditLimit);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/statements")
    @SaCheckPermission("credit:statement:generate")
    @Operation(summary = "生成赊销对账单")
    public ResponseEntity<Void> generateStatement(@Valid @RequestBody CreditStatementGenerateCommand command) {
        creditApplicationService.generateStatement(command);
        return ResponseEntity.ok().build();
    }
}






