package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.dto.vo.AssignedDeliveryVO;
import com.agri_system.application.order.service.DeliveryTaskService;
import com.agri_system.application.order.service.OrderApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/mobile/delivery/tasks")
@SaCheckPermission("order:delivery:assigned")
@RequiredArgsConstructor
public class MobileDeliveryController {
    private final DeliveryTaskService tasks;
    private final OrderApplicationService orders;
    @ModelAttribute
    public void privateTaskResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
    }
    public record Confirmation(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String confirmationKey) {}
    @GetMapping
    public AssignedDeliveryVO.Tasks list(@RequestParam(defaultValue="PENDING") String state,@RequestParam(required=false) Long after) {
        return tasks.listAssignedTasks(CurrentUser.staffId(),state,after);
    }
    @GetMapping("/{orderId}")
    public AssignedDeliveryVO.Preview preview(@PathVariable Long orderId) {
        return orders.previewAssignedReceipt(orderId,CurrentUser.staffId());
    }
    @PostMapping("/{orderId}/confirm")
    public AssignedDeliveryVO.Result confirm(@PathVariable Long orderId,@Valid @RequestBody Confirmation request) {
        return orders.confirmAssignedReceipt(orderId,request.confirmationKey(),CurrentUser.staffId());
    }
}
