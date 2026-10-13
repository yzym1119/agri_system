package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.application.purchase.dto.command.PurchaseCancelCommand;
import com.agri_system.application.purchase.dto.command.PurchaseCreateCommand;
import com.agri_system.application.purchase.dto.command.PurchaseInboundCommand;
import com.agri_system.application.purchase.dto.command.PurchaseSubmitCommand;
import com.agri_system.application.purchase.dto.command.ReceivePurchaseCommand;
import com.agri_system.application.purchase.dto.query.PurchasePageQuery;
import com.agri_system.application.purchase.dto.vo.PurchaseDetailVO;
import com.agri_system.application.purchase.dto.vo.PurchasePageVO;
import com.agri_system.application.purchase.service.PurchaseApplicationService;
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
import java.util.Map;

/**
 * 采购管理控制器
 * 仅限 ADMIN 和 MANAGER 访问
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping({"/api/admin/purchase", "/api/admin/purchases"})
@Tag(name = "采购管理", description = "采购单创建、提交、入库、查询")
@RequiredArgsConstructor
public class PurchaseController {
    
    private final PurchaseApplicationService purchaseService;
    
    // ==================== 分页查询 ====================

    @GetMapping("/list")
    @Operation(summary = "分页查询采购单", description = "支持门店、供应商名、单号、状态、创建时间范围筛选")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("purchase:query")
    public ResponseEntity<Map<String, Object>> list(@Valid PurchasePageQuery query) {
        Page<PurchasePageVO> page = purchaseService.page(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", page);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 创建采购单 ====================
    
    @PostMapping("/create")
    @Operation(summary = "创建采购单", description = "创建新的采购单，状态为待提交")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或商品/门店不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("purchase:create")
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody PurchaseCreateCommand command) {
        Long orderId = purchaseService.create(command, getCurrentUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("orderId", orderId));
        return ResponseEntity.ok(result);
    }
    
    // ==================== 提交采购单 ====================
    
    @PostMapping("/submit")
    @Operation(summary = "提交采购单", description = "将待提交状态的采购单提交为待入库状态")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "提交成功"),
        @ApiResponse(responseCode = "400", description = "采购单不存在或状态不允许提交", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("purchase:submit")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody PurchaseSubmitCommand command) {
        purchaseService.submit(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "提交成功");
        return ResponseEntity.ok(result);
    }
    
    // ==================== 采购入库 ====================
    
    @PostMapping("/inbound")
    @Operation(summary = "采购入库", description = "将待入库状态的采购单入库，触发库存模块入库并更新商品平均成本")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "入库成功"),
        @ApiResponse(responseCode = "400", description = "采购单不存在、已入库或状态不允许入库", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("purchase:inbound")
    public ResponseEntity<Map<String, Object>> inbound(@Valid @RequestBody PurchaseInboundCommand command) {
        purchaseService.inbound(command, getCurrentUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "入库成功");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/receipts")
    @Operation(summary = "采购收货", description = "按采购明细记录一个或多个实际收货批次，保留单位、成本和日期事实")
    @SaCheckPermission("purchase:inbound")
    public ResponseEntity<Map<String, Object>> receive(@Valid @RequestBody ReceivePurchaseCommand command) {
        Long receiptId = purchaseService.receive(command, getCurrentUserId());
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "收货成功");
        result.put("data", Map.of("receiptId", receiptId, "batches", purchaseService.receiptLabels(receiptId)));
        return ResponseEntity.ok(result);
    }
    
    // ==================== 取消采购单 ====================
    
    @PostMapping("/cancel")
    @Operation(summary = "取消采购单", description = "取消待提交或待入库状态的采购单，已入库的不能取消")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "取消成功"),
        @ApiResponse(responseCode = "400", description = "采购单不存在或状态不允许取消", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("purchase:cancel")
    public ResponseEntity<Map<String, Object>> cancel(@Valid @RequestBody PurchaseCancelCommand command) {
        purchaseService.cancel(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "取消成功");
        return ResponseEntity.ok(result);
    }
    
    // ==================== 查询采购单详情 ====================
    
    @GetMapping("/{orderId}")
    @Operation(summary = "查询采购单详情", description = "查询采购单详细信息，包含明细列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "采购单不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("purchase:query")
    public ResponseEntity<Map<String, Object>> getDetail(
            @Parameter(description = "采购单ID", required = true) @PathVariable Long orderId) {
        PurchaseDetailVO vo = purchaseService.getDetail(orderId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", vo);
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







