package com.agri_system.api.client.controller;

import com.agri_system.application.auth.dto.ClientWechatLoginCommand;
import com.agri_system.application.auth.service.ClientOnboardingService;
import com.agri_system.application.auth.service.CustomerEnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/auth/client/onboarding")
@RequiredArgsConstructor
public class ClientOnboardingController {
    private final ClientOnboardingService service;
    private final CustomerEnrollmentService enrollment;
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @PostMapping("/wechat")
    @Operation(summary="开始微信授权",description="既有启用绑定正常登录；未关联只发短时核验凭据，不创建客户")
    public ClientOnboardingService.Reply prepare(@Valid @RequestBody ClientWechatLoginCommand command){return service.prepare(command);}
    @PostMapping("/phone")
    @Operation(summary="核验主动授权的手机号",description="须短时微信核验凭据及平台phoneCode；成功仅PHONE_VERIFIED，不代表开户或旧档案认领")
    public ClientOnboardingService.Reply phone(@Valid @RequestBody ClientWechatLoginCommand command){return service.authorizePhone(command);}
    @GetMapping("/info")
    @Operation(summary="读取当前授权阶段",description="只返回截止时间和脱敏号码，不返回客户或账款")
    public ClientOnboardingService.Reply info(){return service.info();}
    @PostMapping("/registration")
    @Operation(summary="提交已核验手机号的开户或关联申请",description="无同号档案才创建；同号只待人工核验，不授予旧账访问")
    public CustomerEnrollmentService.Result register(@Valid @RequestBody CustomerEnrollmentService.Register command){return enrollment.register(command);}
    @GetMapping("/registration/{requestKey}")
    @Operation(summary="核对原申请结果",description="仅真实同微信身份；UNKNOWN不证明未提交，固定原UUID重试")
    public CustomerEnrollmentService.Result result(@PathVariable String requestKey){return enrollment.result(requestKey);}
}
