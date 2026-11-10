package com.agri_system.api.admin.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.application.store.service.StorePublicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ModelAttribute;
@RestController
@RequestMapping("/api/admin/stores/{storeId}/publication")
@SaCheckPermission(value={"store:view","store:publish"},mode=SaMode.AND)
@RequiredArgsConstructor
public class StorePublicationController {
    private final StorePublicationService service;
    public record Setting(@NotNull Boolean visible,@NotNull @Pattern(regexp="[1-9][0-9]{0,18}") String expectedVersion,@NotNull @Pattern(regexp="[0-9a-f]{64}") String fingerprint) {}
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping public StorePublicationService.Preview preview(@PathVariable Long storeId){return service.preview(CurrentUser.staffId(),storeId);}
    @PostMapping public StorePublicationService.Preview update(@PathVariable Long storeId,@Valid @RequestBody Setting command){return service.setVisible(CurrentUser.staffId(),storeId,command.visible(),command.expectedVersion(),command.fingerprint());}
}
