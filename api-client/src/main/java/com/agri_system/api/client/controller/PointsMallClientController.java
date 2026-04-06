package com.agri_system.api.client.controller;

import com.agri_system.application.common.PageResult;
import com.agri_system.application.points.dto.PointsExchangeCommand;
import com.agri_system.application.points.dto.PointsExchangeOrderVO;
import com.agri_system.application.points.dto.PointsGoodsVO;
import com.agri_system.application.points.service.PointsMallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/client/points")
@Tag(name = "积分商城（客户端）")
@RequiredArgsConstructor
public class PointsMallClientController {

    private final PointsMallService pointsMallService;

    @GetMapping("/goods")
    @Operation(summary = "积分礼品列表")
    public ResponseEntity<List<PointsGoodsVO>> listGoods() {
        return ResponseEntity.ok(pointsMallService.listGoods());
    }

    @GetMapping("/goods/{id}")
    @Operation(summary = "积分礼品详情")
    public ResponseEntity<PointsGoodsVO> detail(@PathVariable Long id) {
        return ResponseEntity.ok(pointsMallService.detail(id));
    }

    @PostMapping("/exchange")
    @Operation(summary = "兑换积分礼品")
    public ResponseEntity<PointsExchangeOrderVO> exchange(@Valid @RequestBody PointsExchangeCommand command,
                                                         @RequestHeader("X-Customer-Id") Long customerId) {
        return ResponseEntity.ok(pointsMallService.exchange(customerId, command));
    }

    @GetMapping("/orders/my")
    @Operation(summary = "我的兑换记录")
    public ResponseEntity<PageResult<PointsExchangeOrderVO>> myOrders(
            @RequestHeader("X-Customer-Id") Long customerId,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return ResponseEntity.ok(pointsMallService.listMyOrders(customerId, pageNum, pageSize));
    }
}

