package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.report.dto.SalesTrendVO;
import com.agri_system.application.report.service.ReportAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售报表Controller
 */
@RestController
@RequestMapping("/api/admin/report/sales")
@Tag(name = "销售报表")
@RequiredArgsConstructor
public class ReportSalesController {

    private final ReportAnalysisService reportAnalysisService;

    @GetMapping("/trend")
    @SaCheckPermission("report:sales")
    @Operation(summary = "销售与毛利趋势")
    public ResponseEntity<List<SalesTrendVO>> salesTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportAnalysisService.salesTrend(startTime, endTime, storeId));
    }

    @GetMapping("/trend/export")
    @SaCheckPermission("report:sales")
    @Operation(summary = "导出销售趋势Excel")
    public void exportSalesTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false) Long storeId,
            HttpServletResponse response) throws IOException {
        List<SalesTrendVO> data = reportAnalysisService.salesTrend(startTime, endTime, storeId).stream()
                .map(row -> {
                    SalesTrendVO exported = new SalesTrendVO();
                    org.springframework.beans.BeanUtils.copyProperties(row, exported);
                    if ("待核对".equals(row.getCostStatus())
                            || (row.getCostWarnings() != null && !row.getCostWarnings().isEmpty())
                            || (row.getCostUnavailableSaleAmount() != null && row.getCostUnavailableSaleAmount().signum() != 0)) {
                        exported.setCostStatus("待核对");
                        exported.setCostAmount(null);
                        exported.setProfit(null);
                        exported.setProfitRate(null);
                    }
                    return exported;
                }).toList();
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        String fileName = "销售趋势_" + startTime.toLocalDate() + "_" + endTime.toLocalDate() + ".xlsx";
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + 
                java.net.URLEncoder.encode(fileName, "UTF-8"));
        
        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), SalesTrendVO.class)
                .sheet("销售趋势")
                .doWrite(data);
    }
}




