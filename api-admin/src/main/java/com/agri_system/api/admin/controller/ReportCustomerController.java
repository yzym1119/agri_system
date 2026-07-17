package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.report.dto.ChurnRiskVO;
import com.agri_system.application.report.dto.CustomerRankVO;
import com.agri_system.application.report.service.ReportAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 客户分析报表Controller
 */
@RestController
@RequestMapping("/api/admin/report/customer")
@Tag(name = "客户分析报表")
@RequiredArgsConstructor
public class ReportCustomerController {

    private final ReportAnalysisService reportAnalysisService;

    @GetMapping("/top")
    @SaCheckPermission("report:customer")
    @Operation(summary = "大户排行榜")
    public ResponseEntity<List<CustomerRankVO>> topCustomers(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportAnalysisService.topCustomers(limit, storeId));
    }

    @GetMapping("/top/export")
    @SaCheckPermission("report:customer")
    @Operation(summary = "导出大户排行榜Excel")
    public void exportTopCustomers(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long storeId,
            HttpServletResponse response) throws IOException {
        List<CustomerRankVO> data = reportAnalysisService.topCustomers(limit, storeId);
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        String fileName = "大户排行榜_" + java.time.LocalDate.now() + ".xlsx";
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" +
                java.net.URLEncoder.encode(fileName, "UTF-8"));

        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), CustomerRankVO.class)
                .sheet("大户排行榜")
                .doWrite(data);
    }

    @GetMapping("/churn")
    @SaCheckPermission("report:customer")
    @Operation(summary = "流失预警")
    public ResponseEntity<List<ChurnRiskVO>> churnRisks(
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportAnalysisService.churnRisks(storeId));
    }

    @GetMapping("/churn/export")
    @SaCheckPermission("report:customer")
    @Operation(summary = "导出流失预警Excel")
    public void exportChurnRisks(
            @RequestParam(required = false) Long storeId,
            HttpServletResponse response) throws IOException {
        List<ChurnRiskVO> data = reportAnalysisService.churnRisks(storeId);
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        String fileName = "流失预警_" + java.time.LocalDate.now() + ".xlsx";
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" +
                java.net.URLEncoder.encode(fileName, "UTF-8"));

        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), ChurnRiskVO.class)
                .sheet("流失预警")
                .doWrite(data);
    }
}




