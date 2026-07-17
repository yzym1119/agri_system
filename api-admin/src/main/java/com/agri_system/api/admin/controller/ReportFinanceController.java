package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.report.dto.CreditRiskVO;
import com.agri_system.application.report.service.ReportAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务报表Controller
 */
@RestController
@RequestMapping("/api/admin/report/finance")
@Tag(name = "财务报表")
@RequiredArgsConstructor
public class ReportFinanceController {

    private final ReportAnalysisService reportAnalysisService;

    @GetMapping("/credit")
    @SaCheckPermission("report:finance:query")
    @Operation(summary = "财务风控分析")
    public ResponseEntity<CreditRiskVO> creditRisk(
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportAnalysisService.creditRisk(storeId));
    }
}




