package com.agri_system.api.client.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
@Tag(name = "健康检查", description = "系统健康检查接口")
public class HealthController {

    @GetMapping
    @Operation(summary = "健康检查", description = "返回系统健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("service", "api-client");
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }
}






















