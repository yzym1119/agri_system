package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.credit.dto.*;
import com.agri_system.application.credit.service.CreditApplicationService;
import com.agri_system.application.credit.service.CreditRepaymentService;
import com.agri_system.domain.credit.model.CreditStatement;
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
    public ResponseEntity<CreditRepaymentVO> repay(@Valid @RequestBody CreditRepayCommand command) {
        return ResponseEntity.ok(creditRepaymentService.repay(command, CurrentUser.staffId()));
    }

    @GetMapping("/accounts")
    @SaCheckPermission("credit:account:view")
    @Operation(summary = "查询欠款单据")
    public ResponseEntity<com.mybatisflex.core.paginate.Page<CreditAccountVO>> listAccounts(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(creditApplicationService.listAccountsPage(customerId, status, storeId, pageNum, pageSize, CurrentUser.staffId()));
    }

    @GetMapping("/repayments")
    @SaCheckPermission("credit:repayment:view")
    @Operation(summary = "查询还款记录")
    public ResponseEntity<List<CreditRepaymentVO>> listRepayments(@RequestParam Long customerId, @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(creditApplicationService.listRepayments(customerId, storeId, CurrentUser.staffId()));
    }

    @GetMapping("/statistics")
    @SaCheckPermission(value={"credit:statistics:view","credit:repay"}, mode=SaMode.OR)
    @Operation(summary = "查询客户赊销统计")
    public ResponseEntity<CreditDebtVO> statistics(@RequestParam Long customerId, @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(creditApplicationService.statistics(customerId, storeId, CurrentUser.staffId()));
    }

    @PutMapping("/limit")
    @SaCheckPermission("credit:limit:update")
    @Operation(summary = "调整客户赊销额度")
    public ResponseEntity<Void> updateLimit(@RequestParam Long customerId,
                                            @RequestParam BigDecimal creditLimit) {
        creditApplicationService.updateCreditLimit(customerId, creditLimit, CurrentUser.staffId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/statements")
    @SaCheckPermission("credit:statement:generate")
    @Operation(summary = "生成赊销对账单")
    public ResponseEntity<CreditStatement> generateStatement(@Valid @RequestBody CreditStatementGenerateCommand command) {
        return ResponseEntity.ok(creditApplicationService.generateStatement(command, CurrentUser.staffId()));
    }
}






