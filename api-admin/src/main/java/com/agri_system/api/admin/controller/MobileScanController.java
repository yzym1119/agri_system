package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.inventory.service.MobileScanQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 员工查货入口；门店始终从当前有效员工读取，不接受客户端指定所有者。 */
@RestController
@RequestMapping("/api/admin/mobile")
@SaCheckPermission("inventory:view")
@RequiredArgsConstructor
public class MobileScanController {
    private final MobileScanQueryService queries;
    public record ScanCommand(@NotBlank @Size(max=100) String code,@NotBlank String purpose) {}
    @PostMapping("/scan/resolve")
    public com.agri_system.domain.inventory.query.MobileInventoryQuery.Resolution resolve(@Valid @RequestBody ScanCommand command) {
        return queries.resolve(command.code(),command.purpose(),CurrentUser.staffId());
    }
    @GetMapping("/inventory/products")
    public com.agri_system.domain.inventory.query.MobileInventoryQuery.Matches search(@RequestParam String keyword,@RequestParam(defaultValue="1") int page) {
        return queries.searchProducts(keyword,page,CurrentUser.staffId());
    }
    @GetMapping("/locations/{locationId}/products")
    public com.agri_system.domain.inventory.query.MobileInventoryQuery.Matches location(@PathVariable Long locationId,@RequestParam(defaultValue="1") int page) {
        return queries.locationProducts(locationId,page,CurrentUser.staffId());
    }
    @GetMapping("/inventory/{productId}")
    public com.agri_system.domain.inventory.query.MobileInventoryQuery.InventoryView inventory(@PathVariable Long productId,@RequestParam(required=false) Long batchId,
            @RequestParam(required=false) Long locationId,@RequestParam(defaultValue="1") int page) {
        return queries.inventory(productId,batchId,locationId,page,CurrentUser.staffId());
    }
}
