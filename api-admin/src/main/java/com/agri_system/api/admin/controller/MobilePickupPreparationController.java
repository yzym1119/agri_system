package com.agri_system.api.admin.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.order.service.MobilePickupPreparationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/admin/mobile/pickup/orders")
@SaCheckPermission("order:pickup:verify")
@RequiredArgsConstructor
public class MobilePickupPreparationController {
    private final MobilePickupPreparationService preparation;
    @ModelAttribute
    public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping("/{orderId}")
    public MobilePickupPreparationService.Preparation read(@PathVariable Long orderId){return preparation.read(orderId,CurrentUser.staffId());}
}
