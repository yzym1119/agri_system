package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.order.service.PosCatalogApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/pos/catalog")
@SaCheckPermission("pos:order:create")
@RequiredArgsConstructor
public class PosCatalogController {
    private final PosCatalogApplicationService catalog;
    @GetMapping("/products")
    public ResponseEntity<Map<String,Object>> products(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="1000") int pageSize,
                                                       @RequestParam(required=false) String keyword) {
        return result(catalog.products(pageNum,pageSize,keyword));
    }
    @GetMapping("/categories")
    public ResponseEntity<Map<String,Object>> categories() {return result(catalog.categories());}
    @GetMapping("/packages")
    public ResponseEntity<Map<String,Object>> packages(@RequestParam(required=false) Long storeId,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="1000") int pageSize) {
        return result(catalog.packages(storeId,pageNum,pageSize));
    }
    @GetMapping("/inventory")
    public ResponseEntity<Map<String,Object>> inventory(@RequestParam(required=false) Long storeId,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="1000") int pageSize) {
        return result(catalog.inventory(storeId,pageNum,pageSize));
    }
    private ResponseEntity<Map<String,Object>> result(Object value) {
        return ResponseEntity.ok(Map.of("code","200","message","查询成功","data",value));
    }
}
