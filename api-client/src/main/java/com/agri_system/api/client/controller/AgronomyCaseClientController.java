package com.agri_system.api.client.controller;

import com.agri_system.application.agronomy.service.AgronomyCaseService;
import com.agri_system.application.auth.service.AuthService;
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
@RequestMapping("/api/client/agronomy/cases")
@RequiredArgsConstructor
public class AgronomyCaseClientController {
    private final AgronomyCaseService service;
    private final AuthService auth;

    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public R<Void> invalidRequest() {
        return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(), "请核对原问题编号、分页与提交内容");
    }
    @GetMapping public AgronomyCaseService.Page page(@RequestParam(required=false) Long after,
                                                    @RequestParam(required=false) String status) {
        return service.pageCustomer(auth.getCurrentCustomerId(), after, status);
    }
    @PostMapping public AgronomyCaseService.Receipt create(@RequestBody AgronomyCaseService.Create command) {
        return service.create(auth.getCurrentCustomerId(), command);
    }
    @GetMapping("/submissions/{requestKey}")
    public AgronomyCaseService.Submission submission(@PathVariable String requestKey) {
        return service.submissionCustomer(auth.getCurrentCustomerId(), requestKey);
    }
    @GetMapping("/{caseId}") public AgronomyCaseService.Detail detail(@PathVariable Long caseId,
                                                                      @RequestParam(required=false) Long after) {
        return service.detailCustomer(auth.getCurrentCustomerId(), caseId, after);
    }
    @PostMapping("/{caseId}/supplements")
    public AgronomyCaseService.Receipt supplement(@PathVariable Long caseId, @RequestBody AgronomyCaseService.Change command) {
        return service.changeCustomer(auth.getCurrentCustomerId(), caseId, "SUPPLEMENT", command);
    }
    @PostMapping("/{caseId}/human-requests")
    public AgronomyCaseService.Receipt requestHuman(@PathVariable Long caseId, @RequestBody AgronomyCaseService.Change command) {
        return service.changeCustomer(auth.getCurrentCustomerId(), caseId, "HUMAN_REQUEST", command);
    }
}
