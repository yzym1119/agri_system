package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.report.dto.InventoryValueVO;
import com.agri_system.application.report.dto.SlowMovingInventoryVO;
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
 * 库存报表Controller
 */
@RestController
@RequestMapping("/api/admin/report/inventory")
@Tag(name = "库存报表")
@RequiredArgsConstructor
public class ReportInventoryController {

    private final ReportAnalysisService reportAnalysisService;

    @GetMapping("/value")
    @SaCheckPermission("report:inventory")
    @Operation(summary = "库存资金占用")
    public ResponseEntity<InventoryValueVO> inventoryValue(
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportAnalysisService.inventoryValue(storeId));
    }

    @GetMapping("/slow-moving")
    @SaCheckPermission("report:inventory")
    @Operation(summary = "滞销商品列表")
    public ResponseEntity<List<SlowMovingInventoryVO>> slowMovingInventory(
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportAnalysisService.slowMovingInventory(storeId));
    }

    @GetMapping("/slow-moving/export")
    @SaCheckPermission("report:inventory")
    @Operation(summary = "导出滞销商品Excel")
    public void exportSlowMoving(
            @RequestParam(required = false) Long storeId,
            HttpServletResponse response) throws IOException {
        List<SlowMovingInventoryVO> data = reportAnalysisService.slowMovingInventory(storeId);
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        String fileName = "滞销商品_" + java.time.LocalDate.now() + ".xlsx";
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" +
                java.net.URLEncoder.encode(fileName, "UTF-8"));

        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), SlowMovingInventoryVO.class)
                .sheet("滞销商品")
                .doWrite(data);
    }
}




