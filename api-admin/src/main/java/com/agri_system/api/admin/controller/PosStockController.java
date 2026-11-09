package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.order.dto.command.PosStockCheckCommand;
import com.agri_system.application.order.service.PosStockPreviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 只读整单物料预检；不创建请求/订单，不预留，不改钱货。 */
@RestController
@RequestMapping("/api/admin/pos/catalog/stock-preview")
@SaCheckPermission("pos:order:create")
@RequiredArgsConstructor
public class PosStockController {
    private final PosStockPreviewService stock;
    @PostMapping
    public PosStockPreviewService.Preview preview(@Valid @RequestBody PosStockCheckCommand command) {
        return stock.preview(command);
    }
}
