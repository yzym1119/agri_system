package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.agri_system.domain.notification.model.SystemNotification;
import com.agri_system.domain.notification.repository.SystemNotificationRepository;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统通知Controller
 */
@RestController
@RequestMapping("/api/admin/notifications")
@Tag(name = "系统通知", description = "站内消息、库存预警、提成通知等")
@RequiredArgsConstructor
public class NotificationController {

    private final SystemNotificationRepository notificationRepository;

    @GetMapping
    @Operation(summary = "查询我的通知列表")
    @SaCheckPermission("notification:query")
    public ResponseEntity<Page<SystemNotification>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 安全：限制分页上限
        pageSize = Math.min(Math.max(pageSize, 1), 200);
        Long staffId = extractStaffId();
        Page<SystemNotification> page = notificationRepository.findPageByStaff(staffId, null, type, isRead, pageNum, pageSize);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "获取未读通知数量")
    @SaCheckPermission("notification:query")
    public ResponseEntity<Map<String, Object>> unreadCount() {
        Long staffId = extractStaffId();
        long count = notificationRepository.countUnread(staffId, null);
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/read")
    @Operation(summary = "批量标记通知已读")
    @SaCheckPermission("notification:query")
    public ResponseEntity<Void> markAsRead(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok().build();
        }
        // 安全：限制最大数量，防止恶意传入大量ID
        if (ids.size() > 200) {
            ids = ids.subList(0, 200);
        }
        Long staffId = extractStaffId();
        notificationRepository.markAsReadByStaff(ids, staffId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    @Operation(summary = "标记所有通知已读")
    @SaCheckPermission("notification:query")
    public ResponseEntity<Void> markAllAsRead() {
        Long staffId = extractStaffId();
        // 获取所有未读通知ID并标记
        Page<SystemNotification> unread = notificationRepository.findPageByStaff(staffId, null, null, 0, 1, 500);
        List<Long> ids = unread.getRecords().stream()
                .map(SystemNotification::getId)
                .toList();
        if (!ids.isEmpty()) {
            notificationRepository.markAsRead(ids);
        }
        return ResponseEntity.ok().build();
    }

    private Long extractStaffId() {
        String loginId = StpUtil.getLoginIdAsString();
        return Long.parseLong(loginId.replace("STAFF_", ""));
    }
}
