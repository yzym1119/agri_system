package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.store.dto.command.StoreCreateCommand;
import com.agri_system.application.store.dto.command.StoreUpdateCommand;
import com.agri_system.application.store.dto.query.StoreQuery;
import com.agri_system.application.store.dto.vo.StoreDetailVO;
import com.agri_system.application.store.service.StoreApplicationService;
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
 * 门店管理控制器
 * 使用权限检查：store:list, store:create, store:update, store:view, store:delete
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/stores")
@Tag(name = "门店管理", description = "门店建档、状态管控、自提点开关、店长绑定")
@RequiredArgsConstructor
public class StoreController {
    
    private final StoreApplicationService storeService;
    
    // ==================== 门店基本档案 ====================
    
    @PostMapping
    @Operation(summary = "创建门店", description = "创建新门店，注意：code 唯一，Update 不可改")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或门店编码已存在", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "店长不存在或已离职", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("store:create")
    public ResponseEntity<Map<String, Object>> createStore(@Valid @RequestBody StoreCreateCommand command) {
        Long storeId = storeService.createStore(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("id", storeId));
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新门店", description = "更新门店信息，注意：code 不可修改")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "门店不存在或店长不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("store:update")
    public ResponseEntity<Map<String, Object>> updateStore(
            @Parameter(description = "门店ID", required = true) @PathVariable Long id,
            @Valid @RequestBody StoreUpdateCommand command) {
        command.setId(id);
        storeService.updateStore(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "更新成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "查询门店详情", description = "查询门店详细信息，包含店长信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "门店不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("store:view")
    public ResponseEntity<Map<String, Object>> getStoreDetail(
            @Parameter(description = "门店ID", required = true) @PathVariable Long id) {
        StoreDetailVO vo = storeService.getStoreDetail(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", vo);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping
    @Operation(summary = "分页查询门店", description = "支持按编码、名称、状态、自提状态筛选")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @SaCheckPermission("store:list")
    public ResponseEntity<Map<String, Object>> queryStores(@Valid StoreQuery query) {
        Page<StoreDetailVO> page = storeService.queryStores(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", page);
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除门店", description = "逻辑删除门店，删除后 code 依然被占用")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "门店不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("store:delete")
    public ResponseEntity<Map<String, Object>> deleteStore(
            @Parameter(description = "门店ID", required = true) @PathVariable Long id) {
        storeService.deleteStore(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }
}










