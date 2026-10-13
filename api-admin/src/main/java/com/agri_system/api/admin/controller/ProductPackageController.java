package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.productpackage.dto.command.ProductPackageCreateCommand;
import com.agri_system.application.productpackage.dto.command.ProductPackageUpdateCommand;
import com.agri_system.application.productpackage.dto.query.ProductPackageQuery;
import com.agri_system.application.productpackage.dto.vo.ProductPackageDetailVO;
import com.agri_system.application.productpackage.service.ProductPackageApplicationService;
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
 * 套餐管理控制器
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/product-packages")
@Tag(name = "套餐管理", description = "套餐主档、明细管理、上下架操作")
@RequiredArgsConstructor
public class ProductPackageController {
    
    private final ProductPackageApplicationService packageService;
    private final com.agri_system.application.productpackage.service.PackagePricingService packagePricingService;
    
    @GetMapping("/{id}/price-quote")
    @Operation(summary = "套餐服务端报价", description = "按真实会员身份取得元/套价格、物料分摊与专用金可用金额")
    @SaCheckPermission(value = {"package:query", "pos:order:create"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public ResponseEntity<Map<String, Object>> quotePackage(@PathVariable Long id,
            @RequestParam(required = false) Long customerId, @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "1") java.math.BigDecimal quantity,
            @RequestParam(required = false) java.math.BigDecimal dealUnitPrice) {
        var quote = packagePricingService.quote(id, customerId, storeId, quantity, dealUnitPrice);
        return ResponseEntity.ok(Map.of("code", "200", "message", "查询成功", "data", quote));
    }

    // ==================== 套餐主档管理 ====================
    
    @PostMapping
    @Operation(summary = "创建套餐", description = "创建新套餐，系统自动生成套餐编码")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或套餐编码重复", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("package:create")
    public ResponseEntity<Map<String, Object>> createPackage(@Valid @RequestBody ProductPackageCreateCommand command) {
        Long packageId = packageService.createPackage(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("id", packageId));
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新套餐", description = "更新套餐信息，采用全量替换明细模式")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "套餐不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("package:update")
    public ResponseEntity<Map<String, Object>> updatePackage(
            @Parameter(description = "套餐ID", required = true) @PathVariable Long id,
            @Valid @RequestBody ProductPackageUpdateCommand command) {
        command.setId(id);
        packageService.updatePackage(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "更新成功");
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除套餐", description = "软删除套餐，不校验订单引用")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "套餐不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("package:delete")
    public ResponseEntity<Map<String, Object>> deletePackage(
            @Parameter(description = "套餐ID", required = true) @PathVariable Long id) {
        packageService.deletePackage(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "查询套餐详情", description = "查询套餐详细信息，包含明细列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "套餐不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission(value = {"package:query", "pos:order:create"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public ResponseEntity<Map<String, Object>> getPackageDetail(
            @Parameter(description = "套餐ID", required = true) @PathVariable Long id) {
        ProductPackageDetailVO vo = packageService.getPackageDetail(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", vo);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping
    @Operation(summary = "分页查询套餐", description = "支持按编码、名称、状态筛选")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @SaCheckPermission(value = {"package:query", "pos:order:create"}, mode = cn.dev33.satoken.annotation.SaMode.OR)
    public ResponseEntity<Map<String, Object>> queryPackages(@Valid ProductPackageQuery query) {
        Page<ProductPackageDetailVO> page = packageService.queryPackages(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", page);
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{id}/status")
    @Operation(summary = "上架/下架套餐", description = "快捷操作：修改套餐上架状态（1=上架，0=下架）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "套餐不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("package:update")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @Parameter(description = "套餐ID", required = true) @PathVariable Long id,
            @Parameter(description = "状态：1=上架，0=下架", required = true) @RequestParam Integer status) {
        packageService.updateStatus(id, status);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "操作成功");
        return ResponseEntity.ok(result);
    }
}









