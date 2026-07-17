package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 打印相关管理接口（stub）
 */
@RestController
@RequestMapping("/api/admin/print")
public class PrintAdminController {
    private static final Logger logger = LoggerFactory.getLogger(PrintAdminController.class);

    /**
     * 接收小票打印请求（前端 buildReceiptPayload 的 JSON）
     * TODO: 集成后端打印服务/生成 PDF 或推送到打印机
     */
    @PostMapping("/receipt")
    @SaCheckLogin
    public ResponseEntity<Map<String, Object>> printReceipt(@RequestBody Map<String, Object> payload) {
        logger.info("Print endpoint disabled - frontend printing only. Received payload orderNo: {}", payload != null ? payload.get("orderNo") : "null");
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "disabled");
        resp.put("message", "后端打印功能已移除，请使用前端的“立即打印”功能。");
        return ResponseEntity.status(410).body(resp);
    }
}


