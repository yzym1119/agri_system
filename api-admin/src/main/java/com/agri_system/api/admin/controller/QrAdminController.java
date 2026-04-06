package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.performance.dto.QrCreateResultVO;
import com.agri_system.application.performance.dto.QrStatusVO;
import com.agri_system.application.performance.service.OneTimeQrService;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "QrAdmin", description = "一次性二维码接口")
@RestController
@RequestMapping("/api/admin/qr")
@RequiredArgsConstructor
public class QrAdminController {

    private final OneTimeQrService oneTimeQrService;

    @PostMapping("/create")
    @SaCheckPermission("qr:create")
    @Operation(summary = "创建一次性二维码")
    public R<QrCreateResultVO> create(@RequestParam String orderTempToken,
                                      @RequestParam Long staffId,
                                      @RequestParam Long storeId) {
        return R.success(oneTimeQrService.create(orderTempToken, staffId, storeId));
    }

    @GetMapping("/status")
    @SaCheckPermission("qr:view")
    @Operation(summary = "查询二维码状态")
    public R<QrStatusVO> status(@RequestParam String qrToken) {
        return R.success(oneTimeQrService.getStatus(qrToken));
    }
}

