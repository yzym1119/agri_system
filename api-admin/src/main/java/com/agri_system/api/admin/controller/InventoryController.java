package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.inventory.dto.command.InventoryAdjustCommand;
import com.agri_system.application.inventory.dto.command.InventoryInCommand;
import com.agri_system.application.inventory.dto.command.InventoryOutCommand;
import com.agri_system.application.inventory.dto.command.InventoryTransferCommand;
import com.agri_system.application.inventory.dto.command.InventoryLocationCommand;
import com.agri_system.application.inventory.dto.command.InventoryMoveCommand;
import com.agri_system.application.inventory.dto.query.InventoryQuery;
import com.agri_system.application.inventory.dto.query.InventoryTransferRecordQuery;
import com.agri_system.application.inventory.dto.query.InventoryTransactionQuery;
import com.agri_system.application.inventory.dto.vo.BatchDetailVO;
import com.agri_system.application.inventory.dto.vo.InventoryDetailVO;
import com.agri_system.application.inventory.dto.vo.InventoryTransferRecordVO;
import com.agri_system.application.inventory.dto.vo.InventoryTransactionVO;
import com.agri_system.application.inventory.service.InventoryApplicationService;
import com.agri_system.domain.inventory.vo.InventoryPageVO;
import com.agri_system.domain.inventory.model.InventoryMovement;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存管理控制器
 * 使用权限检查：inventory:inbound, inventory:outbound, inventory:transfer, inventory:view, inventory:batch:view
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/inventory")
@Tag(name = "库存管理", description = "批次管理、FIFO扣减、库存流水、强一致性计算")
@RequiredArgsConstructor
public class InventoryController {
    
    private final InventoryApplicationService inventoryService;

    @PostMapping("/locations")
    @Operation(summary = "创建库存位置")
    @SaCheckPermission("inventory:adjust")
    public ResponseEntity<Map<String, Object>> createLocation(@Valid @RequestBody InventoryLocationCommand command) {
        return ResponseEntity.ok(Map.of("code", "200", "message", "创建成功",
                "data", Map.of("locationId", inventoryService.createLocation(command))));
    }

    @PostMapping("/moves")
    @Operation(summary = "批次货位移动")
    @SaCheckPermission("inventory:adjust")
    public ResponseEntity<Map<String, Object>> move(@Valid @RequestBody InventoryMoveCommand command) {
        InventoryMovement movement = inventoryService.move(command, getCurrentUserId());
        return ResponseEntity.ok(Map.of("code", "200", "message", "移动成功", "data", movement));
    }
    
    // ==================== 入库 ====================
    
    @PostMapping("/inbound")
    @Operation(summary = "入库", description = "商品入库，支持批次合并与加权平均成本计算")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "入库成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:inbound")
    public ResponseEntity<Map<String, Object>> inbound(@Valid @RequestBody InventoryInCommand command) {
        Long batchId = inventoryService.inbound(command, getCurrentUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "入库成功");
        result.put("data", Map.of("batchId", batchId));
        return ResponseEntity.ok(result);
    }
    
    // ==================== 出库 ====================
    
    @PostMapping("/outbound")
    @Operation(summary = "出库（FIFO）", description = "商品出库，按FIFO规则扣减批次库存")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "出库成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或库存不足", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:outbound")
    public ResponseEntity<Map<String, Object>> outbound(@Valid @RequestBody InventoryOutCommand command) {
        List<BatchDetailVO> batchDetails = inventoryService.outbound(command, getCurrentUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "出库成功");
        result.put("data", Map.of("batchDetails", batchDetails));
        return ResponseEntity.ok(result);
    }
    
    // ==================== 调拨 ====================
    
    @PostMapping("/transfer")
    @Operation(summary = "调拨", description = "门店间库存调拨，出库端FIFO，入库端保持原成本价")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "调拨成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或库存不足", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:transfer")
    public ResponseEntity<Map<String, Object>> transfer(@Valid @RequestBody InventoryTransferCommand command) {
        var transferDetail = inventoryService.transfer(command, getCurrentUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "调拨成功");
        result.put("data", transferDetail);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 查询 ====================
    
    @GetMapping("/detail")
    @Operation(summary = "查询库存详情", description = "查询指定门店和商品的库存详情，包含批次列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:view")
    public ResponseEntity<Map<String, Object>> getInventoryDetail(
            @Parameter(description = "门店ID", required = true) @RequestParam Long storeId,
            @Parameter(description = "商品ID", required = true) @RequestParam Long productId) {
        InventoryDetailVO vo = inventoryService.getInventoryDetail(storeId, productId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", vo);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/batch/{batchId}")
    @Operation(summary = "查询批次详情", description = "查询批次详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "批次不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:batch:view")
    public ResponseEntity<Map<String, Object>> getBatchDetail(
            @Parameter(description = "批次ID", required = true) @PathVariable Long batchId) {
        BatchDetailVO vo = inventoryService.getBatchDetail(batchId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", vo);
        return ResponseEntity.ok(result);
    }

    // ==================== 分页查询 ====================

    @GetMapping("/list")
    @Operation(summary = "分页查询库存", description = "按门店/商品名称查询库存快照列表")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:view")
    public ResponseEntity<Map<String, Object>> queryInventoryList(@Valid InventoryQuery query) {
        Page<InventoryPageVO> page = inventoryService.queryInventoryPage(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", page);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 库存调整（盘点） ====================
    
    @PostMapping("/adjust")
    @Operation(summary = "库存调整（盘点）", description = "库存盘点调整，支持盘盈和盘亏")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "调整成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或库存不足", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:adjust")
    public ResponseEntity<Map<String, Object>> adjust(@Valid @RequestBody InventoryAdjustCommand command) {
        inventoryService.adjust(command, getCurrentUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "调整成功");
        return ResponseEntity.ok(result);
    }
    
    // ==================== 库存流水查询 ====================
    
    @GetMapping("/transactions")
    @Operation(summary = "分页查询库存流水", description = "查询库存变动流水记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("inventory:log:list")
    public ResponseEntity<Map<String, Object>> queryTransactions(@Valid InventoryTransactionQuery query) {
        Page<InventoryTransactionVO> page = inventoryService.queryTransactionPage(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", page);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/transfers")
    @Operation(summary = "分页查询调拨记录", description = "按调拨单号聚合查询门店调拨记录")
    @SaCheckPermission("inventory:transfer")
    public ResponseEntity<Map<String, Object>> queryTransfers(@Valid InventoryTransferRecordQuery query) {
        Page<InventoryTransferRecordVO> page = inventoryService.queryTransferPage(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", page);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 私有方法 ====================
    
    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        if (!StpUtil.isLogin()) {
            throw new IllegalArgumentException("未登录");
        }
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId != null && loginId.startsWith("STAFF_")) {
            return Long.parseLong(loginId.substring("STAFF_".length()));
        }
        throw new IllegalArgumentException("无法获取用户ID");
    }
}















