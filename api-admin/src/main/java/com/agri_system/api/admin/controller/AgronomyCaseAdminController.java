package com.agri_system.api.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.agri_system.application.agronomy.service.AgronomyCaseService;
import com.agri_system.application.auth.util.CurrentUser;
import com.agri_system.domain.common.error.SystemErrorCode;
import com.agri_system.infrastructure.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/admin/agronomy/cases")
@SaCheckPermission("agronomy:case:query")
@RequiredArgsConstructor
public class AgronomyCaseAdminController {
    private final AgronomyCaseService service;
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public R<Void> invalidRequest() {
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), "请核对原问题编号、分页与提交内容");
    }
    @GetMapping public AgronomyCaseService.WorkPage page(@RequestParam(required=false) Long storeId,
            @RequestParam(required=false) Long after, @RequestParam(required=false) String status) {
        return service.pageStaff(CurrentUser.staffId(), storeId, after, status);
    }
    @GetMapping("/submissions/{requestKey}")
    public AgronomyCaseService.Submission submission(@PathVariable String requestKey) {
        return service.submissionStaff(CurrentUser.staffId(), requestKey);
    }
    @GetMapping("/{caseId}") public AgronomyCaseService.WorkDetail detail(@PathVariable Long caseId,
                                                                          @RequestParam(required=false) Long after) {
        return service.detailStaff(CurrentUser.staffId(), caseId, after);
    }
    @PostMapping("/{caseId}/assignment")
    @SaCheckPermission(value={"agronomy:case:query", "agronomy:case:manage"}, mode=SaMode.AND)
    public AgronomyCaseService.Receipt assign(@PathVariable Long caseId, @RequestBody AgronomyCaseService.Change command) {
        return service.changeStaff(CurrentUser.staffId(), caseId, "ASSIGN", command);
    }
    @PostMapping("/{caseId}/replies")
    @SaCheckPermission(value={"agronomy:case:query", "agronomy:case:review"}, mode=SaMode.AND)
    public AgronomyCaseService.Receipt reply(@PathVariable Long caseId, @RequestBody AgronomyCaseService.Change command) {
        return service.changeStaff(CurrentUser.staffId(), caseId, "HUMAN_REPLY", command);
    }
}
