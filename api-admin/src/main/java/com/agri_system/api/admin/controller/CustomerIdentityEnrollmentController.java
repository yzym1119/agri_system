package com.agri_system.api.admin.controller;

import com.agri_system.application.auth.service.CustomerEnrollmentReviewService;
import com.agri_system.application.auth.service.CustomerEnrollmentService;
import com.agri_system.application.auth.util.CurrentUser;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/admin/customer-identities/enrollments")
@RequiredArgsConstructor
public class CustomerIdentityEnrollmentController {
    private final CustomerEnrollmentReviewService service;
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    @GetMapping
    @SaCheckPermission({"customer:identity:query","customer:query"})
    public CustomerEnrollmentReviewService.Page list(@RequestParam(required=false) String status,@RequestParam(required=false) Long before){return service.list(CurrentUser.staffId(),status,before);}
    @GetMapping("/candidates")
    @SaCheckPermission({"customer:identity:query","customer:query"})
    public java.util.List<CustomerEnrollmentReviewService.Candidate> candidates(@RequestParam String keyword){return service.searchCandidates(CurrentUser.staffId(),keyword);}
    @GetMapping("/{id}")
    @SaCheckPermission({"customer:identity:query","customer:query"})
    public CustomerEnrollmentReviewService.Detail detail(@PathVariable Long id){return service.detail(CurrentUser.staffId(),id);}
    @GetMapping("/{id}/decisions/{requestKey}")
    @SaCheckPermission({"customer:identity:query","customer:query"})
    public CustomerEnrollmentReviewService.DecisionResult decisionResult(@PathVariable Long id,@PathVariable String requestKey){return service.decisionResult(CurrentUser.staffId(),id,requestKey);}
    @PostMapping("/{id}/decision")
    @SaCheckPermission({"customer:identity:bind","customer:query"})
    public CustomerEnrollmentService.Result decide(@PathVariable Long id,@Valid @RequestBody CustomerEnrollmentReviewService.Decision command){return service.decide(CurrentUser.staffId(),id,command);}
}
