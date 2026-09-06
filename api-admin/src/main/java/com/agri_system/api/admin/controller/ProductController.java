package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.product.dto.command.*;
import com.agri_system.application.product.dto.query.ProductQuery;
import com.agri_system.application.product.dto.vo.CategoryTreeVO;
import com.agri_system.application.product.dto.vo.PriceRuleVO;
import com.agri_system.application.product.dto.vo.ProductDetailVO;
import com.agri_system.application.product.dto.vo.ProductUnitVO;
import com.agri_system.application.product.dto.vo.UnitConversionVO;
import com.agri_system.application.product.service.ProductApplicationService;
import com.agri_system.application.storage.dto.FileUploadCommand;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品管理控制器
 * 
 * @author AI Generated
 */
@RestController
@RequestMapping("/api/admin/products")
@Tag(name = "商品管理", description = "商品档案、分类、单位换算、价格体系管理")
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductApplicationService productService;
    
    // ==================== 商品基本档案 ====================
    
    @PostMapping
    @Operation(summary = "创建商品", description = "创建新商品，注意：avg_cost_price字段只读，由采购模块维护")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或商品编码已存在", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "分类不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:create")
    public ResponseEntity<Map<String, Object>> createProduct(@Valid @RequestBody ProductCreateCommand command) {
        Long productId = productService.createProduct(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("id", productId));
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "更新商品", description = "更新商品信息，注意：avg_cost_price字段只读，由采购模块维护")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:update")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @Parameter(description = "商品ID", required = true) @PathVariable Long id,
            @Valid @RequestBody ProductUpdateCommand command) {
        command.setId(id);
        productService.updateProduct(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "更新成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "查询商品详情", description = "查询商品详细信息，包含主辅单位信息、分类名称")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:query")
    public ResponseEntity<Map<String, Object>> getProductDetail(
            @Parameter(description = "商品ID", required = true) @PathVariable Long id) {
        ProductDetailVO vo = productService.getProductDetail(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", vo);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping
    @Operation(summary = "分页查询商品", description = "支持按编码、名称、分类ID、上架状态、创建时间范围筛选")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @SaCheckPermission("product:query")
    public ResponseEntity<Map<String, Object>> queryProducts(@Valid ProductQuery query) {
        Page<ProductDetailVO> page = productService.queryProducts(query);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", page);
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{id}/online-status")
    @Operation(summary = "上架/下架商品", description = "快捷操作：修改商品上架状态")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "操作成功"),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:update")
    public ResponseEntity<Map<String, Object>> updateOnlineStatus(
            @Parameter(description = "商品ID", required = true) @PathVariable Long id,
            @Parameter(description = "上架状态：1-上架，0-下架", required = true) @RequestParam Integer isOnline) {
        productService.updateOnlineStatus(id, isOnline);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "操作成功");
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品", description = "删除商品，需校验是否被订单引用")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:delete")
    public ResponseEntity<Map<String, Object>> deleteProduct(
            @Parameter(description = "商品ID", required = true) @PathVariable Long id) {
        productService.deleteProduct(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/image/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传商品主图", description = "上传图片到阿里云 OSS，并返回可访问 URL")
    @SaCheckPermission("product:create")
    public ResponseEntity<Map<String, Object>> uploadProductImage(
            @Parameter(description = "待上传文件", required = true)
            @RequestPart("file") MultipartFile file) {
        FileUploadCommand command = FileUploadCommand.of(
                "product",
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file::getInputStream);
        String url = productService.uploadProductImage(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "上传成功");
        result.put("data", Map.of("url", url));
        return ResponseEntity.ok(result);
    }
    
    // ==================== 商品分类管理 ====================
    
    @PostMapping("/categories")
    @Operation(summary = "创建分类", description = "创建商品分类，支持树形结构")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或分类编码已存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:category:create")
    public ResponseEntity<Map<String, Object>> createCategory(@Valid @RequestBody CategoryCreateCommand command) {
        Long categoryId = productService.createCategory(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("id", categoryId));
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/categories/{id}")
    @Operation(summary = "更新分类", description = "更新分类名称、图标、排序")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "404", description = "分类不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:category:update")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @Parameter(description = "分类ID", required = true) @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateCommand command) {
        command.setId(id);
        productService.updateCategory(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "更新成功");
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/categories/{id}")
    @Operation(summary = "删除分类", description = "删除分类，需校验是否被商品引用")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "400", description = "分类下存在商品，无法删除", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "分类不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:category:delete")
    public ResponseEntity<Map<String, Object>> deleteCategory(
            @Parameter(description = "分类ID", required = true) @PathVariable Long id) {
        productService.deleteCategory(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/categories/tree")
    @Operation(summary = "查询分类树", description = "递归构建树形结构")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @SaCheckPermission("product:category:query")
    public ResponseEntity<Map<String, Object>> getCategoryTree() {
        List<CategoryTreeVO> tree = productService.getCategoryTree();
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", tree);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 多单位管理 ====================

    @GetMapping("/{productId}/units")
    @Operation(summary = "查询商品业务单位", description = "查询 SKU 的启用业务单位；旧主辅单位以只读兼容投影返回")
    @SaCheckPermission("product:unit:query")
    public ResponseEntity<Map<String, Object>> getProductUnits(
            @Parameter(description = "商品ID", required = true) @PathVariable Long productId) {
        List<ProductUnitVO> units = productService.getProductUnits(productId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", units);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{productId}/units")
    @Operation(summary = "完整替换商品业务单位", description = "原子校验并替换 SKU 的全部业务单位")
    // 复用已在 V49 初始化的单位管理权限，避免部署库缺少 product:unit:update 导致接口不可用。
    @SaCheckPermission("product:unit:create")
    public ResponseEntity<Map<String, Object>> replaceProductUnits(
            @Parameter(description = "商品ID", required = true) @PathVariable Long productId,
            @Valid @RequestBody List<ProductUnitUpsertCommand> units) {
        productService.replaceProductUnits(productId, units);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "更新成功");
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/unit-conversions")
    @Operation(summary = "创建单位换算", description = "增加扩展单位换算配置")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或单位换算配置冲突", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:unit:create")
    public ResponseEntity<Map<String, Object>> createUnitConversion(@Valid @RequestBody UnitConversionCreateCommand command) {
        Long conversionId = productService.createUnitConversion(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("id", conversionId));
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/unit-conversions/{id}")
    @Operation(summary = "删除单位换算", description = "删除指定的单位换算配置")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "单位换算不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:unit:delete")
    public ResponseEntity<Map<String, Object>> deleteUnitConversion(
            @Parameter(description = "单位换算ID", required = true) @PathVariable Long id) {
        productService.deleteUnitConversion(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{productId}/unit-conversions")
    @Operation(summary = "查询单位换算列表", description = "查询某商品的所有单位换算配置")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @SaCheckPermission("product:unit:query")
    public ResponseEntity<Map<String, Object>> getUnitConversions(
            @Parameter(description = "商品ID", required = true) @PathVariable Long productId) {
        List<UnitConversionVO> conversions = productService.getUnitConversions(productId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", conversions);
        return ResponseEntity.ok(result);
    }
    
    // ==================== 多维价格体系 ====================
    
    @PostMapping("/price-rules")
    @Operation(summary = "创建价格规则", description = "新增/更新价格规则（策略价格），store_id和vip_level可以为空")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "商品不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:price:create")
    public ResponseEntity<Map<String, Object>> createPriceRule(@Valid @RequestBody PriceRuleCreateCommand command) {
        Long priceRuleId = productService.createPriceRule(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "创建成功");
        result.put("data", Map.of("id", priceRuleId));
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/price-rules/{id}")
    @Operation(summary = "更新价格规则", description = "更新价格规则信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "更新成功"),
        @ApiResponse(responseCode = "404", description = "价格规则不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:price:update")
    public ResponseEntity<Map<String, Object>> updatePriceRule(
            @Parameter(description = "价格规则ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PriceRuleUpdateCommand command) {
        command.setId(id);
        productService.updatePriceRule(command);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "更新成功");
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/price-rules/{id}")
    @Operation(summary = "删除价格规则", description = "删除指定的价格规则")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "价格规则不存在", content = @Content(schema = @Schema(implementation = Map.class)))
    })
    @SaCheckPermission("product:price:delete")
    public ResponseEntity<Map<String, Object>> deletePriceRule(
            @Parameter(description = "价格规则ID", required = true) @PathVariable Long id) {
        productService.deletePriceRule(id);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "删除成功");
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/{productId}/price-rules")
    @Operation(summary = "查询价格规则列表", description = "查询某商品的所有价格规则")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功")
    })
    @SaCheckPermission("product:price:query")
    public ResponseEntity<Map<String, Object>> getPriceRules(
            @Parameter(description = "商品ID", required = true) @PathVariable Long productId) {
        List<PriceRuleVO> priceRules = productService.getPriceRules(productId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", "200");
        result.put("message", "查询成功");
        result.put("data", priceRules);
        return ResponseEntity.ok(result);
    }
}




