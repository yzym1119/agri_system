package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.purchase.dto.command.SupplierCreateCommand;
import com.agri_system.application.purchase.dto.command.SupplierUpdateCommand;
import com.agri_system.application.purchase.dto.query.SupplierQuery;
import com.agri_system.application.purchase.dto.vo.SupplierDetailVO;
import com.agri_system.application.purchase.service.SupplierApplicationService;
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
 * 供应商管理控制器
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/suppliers")
@Tag(name = "供应商管理", description = "供应商信息管理")
@RequiredArgsConstructor
public class SupplierController {
    
    private final SupplierApplicationService supplierService;
    
    @PostMapping
    @Operation(summary = "创建供应商", description = "创建新供应商，code可选，不填则自动生成")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或供应商编码已存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("supplier:create")
    public ResponseEntity<Map<String, Object>> createSupplier(@Valid @RequestBody SupplierCreateCommand command) {
        Long supplierId = supplierService.createSupplier(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("id", supplierId));
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新供应商", description = "更新供应商信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "供应商不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("supplier:update")
    public ResponseEntity<Map<String, Object>> updateSupplier(
            @Parameter(description = "供应商ID", required = true) @PathVariable Long id,
            @Valid @RequestBody SupplierUpdateCommand command) {
        command.setId(id);
        supplierService.updateSupplier(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "更新成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "查询供应商详情", description = "查询供应商详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "供应商不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("supplier:view")
    public ResponseEntity<Map<String, Object>> getSupplierDetail(
            @Parameter(description = "供应商ID", required = true) @PathVariable Long id) {
        SupplierDetailVO detail = supplierService.getSupplierDetail(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", detail);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping
    @Operation(summary = "分页查询供应商", description = "支持按编码、名称、状态查询")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @SaCheckPermission("supplier:list")
    public ResponseEntity<Map<String, Object>> querySuppliers(@Valid SupplierQuery query) {
        Page<SupplierDetailVO> page = supplierService.querySuppliers(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", Map.of(
                "records", page.getRecords(),
                "total", page.getTotalRow(),
                "pageNum", page.getPageNumber(),
                "pageSize", page.getPageSize()
        ));
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除供应商", description = "逻辑删除供应商")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "供应商不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("supplier:delete")
    public ResponseEntity<Map<String, Object>> deleteSupplier(
            @Parameter(description = "供应商ID", required = true) @PathVariable Long id) {
        supplierService.deleteSupplier(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }
}

