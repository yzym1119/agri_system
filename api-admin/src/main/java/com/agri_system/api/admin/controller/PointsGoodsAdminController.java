package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.points.dto.PointsGoodsSaveCommand;
import com.agri_system.application.points.dto.PointsGoodsVO;
import com.agri_system.application.points.service.PointsFulfillmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/points/goods")
@Tag(name = "积分礼品管理")
@RequiredArgsConstructor
public class PointsGoodsAdminController {

    private final PointsFulfillmentService fulfillmentService;

    @GetMapping
    @SaCheckPermission("marketing:points:goods:query")
    @Operation(summary = "积分礼品列表")
    public ResponseEntity<List<PointsGoodsVO>> list() {
        return ResponseEntity.ok(fulfillmentService.listAllGoods());
    }

    @PostMapping
    @SaCheckPermission("marketing:points:goods:create")
    @Operation(summary = "新增积分礼品")
    public ResponseEntity<PointsGoodsVO> create(@Valid @RequestBody PointsGoodsSaveCommand command) {
        return ResponseEntity.ok(fulfillmentService.createGoods(command));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("marketing:points:goods:update")
    @Operation(summary = "编辑积分礼品")
    public ResponseEntity<PointsGoodsVO> update(@PathVariable Long id,
                                                @Valid @RequestBody PointsGoodsSaveCommand command) {
        return ResponseEntity.ok(fulfillmentService.updateGoods(id, command));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("marketing:points:goods:delete")
    @Operation(summary = "删除积分礼品")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fulfillmentService.deleteGoods(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/on")
    @SaCheckPermission("marketing:points:goods:update")
    @Operation(summary = "上架积分礼品")
    public ResponseEntity<Void> online(@PathVariable Long id) {
        fulfillmentService.switchGoods(id, true);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/off")
    @SaCheckPermission("marketing:points:goods:update")
    @Operation(summary = "下架积分礼品")
    public ResponseEntity<Void> offline(@PathVariable Long id) {
        fulfillmentService.switchGoods(id, false);
        return ResponseEntity.ok().build();
    }
}

