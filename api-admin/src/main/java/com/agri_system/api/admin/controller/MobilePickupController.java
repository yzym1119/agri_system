package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.service.MobilePickupTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 手机只读适配；确认仍使用原提货履约入口及其订单锁、数量摘要和防重。 */
@RestController
@RequestMapping("/api/admin/mobile/pickup/tasks")
@SaCheckPermission("order:pickup:verify")
@RequiredArgsConstructor
public class MobilePickupController {
    private final MobilePickupTaskService tasks;

    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
    }

    @GetMapping
    public MobilePickupTaskService.Tasks list(@RequestParam(defaultValue="PENDING") String state,
                                              @RequestParam(required=false) Long after) {
        return tasks.listTasks(CurrentUser.staffId(),state,after);
    }

    @GetMapping("/{requestId}")
    public MobilePickupTaskService.Preview preview(@PathVariable Long requestId) {
        return tasks.preview(requestId,CurrentUser.staffId());
    }
}
