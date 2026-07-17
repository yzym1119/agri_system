package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.report.dto.DashboardVO;
import com.agri_system.application.report.service.ReportAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/report/dashboard")
@Tag(name = "经营驾驶舱报表")
@RequiredArgsConstructor
public class ReportDashboardController {

    private final ReportAnalysisService reportAnalysisService;

    @GetMapping("/today")
    @SaCheckPermission("report:dashboard")
    @Operation(summary = "今日经营驾驶舱")
    public ResponseEntity<DashboardVO> today(
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportAnalysisService.dashboardToday(storeId));
    }
}





