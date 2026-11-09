package com.agri_system.api.client.controller;

import com.agri_system.application.performance.dto.QrBindCommand;
import com.agri_system.infrastructure.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "QrClient", description = "小程序扫码绑定接口")
@RestController
@RequestMapping("/api/client/auth")
public class QrClientController {

    @PostMapping("/qrBind")
    @Operation(summary = "旧扫码关联入口已关闭", description="不支持以自报customerId/OpenID认领身份或改变归属")
    public R<Void> bind(@Valid @RequestBody QrBindCommand command) {
        throw new IllegalStateException("旧扫码关联入口已关闭，请由门店核验客户身份；不会按自报编号改变客户归属");
    }
}

