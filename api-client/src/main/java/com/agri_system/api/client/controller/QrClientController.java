package com.agri_system.api.client.controller;

import com.agri_system.application.performance.dto.QrBindCommand;
import com.agri_system.application.performance.service.OneTimeQrService;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QrClient", description = "小程序扫码绑定接口")
@RestController
@RequestMapping("/api/client/auth")
@RequiredArgsConstructor
public class QrClientController {

    private final OneTimeQrService oneTimeQrService;

    @PostMapping("/qrBind")
    @Operation(summary = "扫码绑定客户信息")
    public R<Void> bind(@Valid @RequestBody QrBindCommand command) {
        oneTimeQrService.bind(command);
        return R.success();
    }
}

