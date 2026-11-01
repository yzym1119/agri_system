package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.service.OrderApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@SaCheckPermission("order:ship")
@RequiredArgsConstructor
public class DeliveryReassignmentController {
    private final OrderApplicationService orders;
    public record Reassignment(@NotBlank @Pattern(regexp="0|[1-9][0-9]{0,18}") String expectedVersion,
                               @NotBlank @Pattern(regexp="[1-9][0-9]{0,18}") String targetStaffId,
                               @NotBlank @Size(max=200) String reason) {}
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/{orderId}/reassignment")
    public OrderApplicationService.ReassignmentPreview preview(@PathVariable Long orderId) {
        return orders.previewDeliveryReassignment(orderId,CurrentUser.staffId());
    }
    @PostMapping("/{orderId}/reassign")
    public ResponseEntity<Void> reassign(@PathVariable Long orderId,@Valid @RequestBody Reassignment command) {
        orders.reassignDelivery(orderId,Long.parseLong(command.expectedVersion()),Long.parseLong(command.targetStaffId()),command.reason(),CurrentUser.staffId());
        return ResponseEntity.ok().build();
    }
}
