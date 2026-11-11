package com.agri_system.api.client.controller;

import com.agri_system.application.agronomy.service.AgronomyAssistantAccessService;
import com.agri_system.application.agronomy.service.AgronomyAssistantCoordinator;
import com.agri_system.domain.common.error.SystemErrorCode;
import com.agri_system.infrastructure.common.response.R;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/client/agronomy/cases/{caseId}/assistant")
@RequiredArgsConstructor
public class AgronomyAssistantClientController {
    private final AgronomyAssistantAccessService service;
    @ModelAttribute public void noCache(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","no-store");}
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class})
    public R<Void> invalidRequest() {return R.fail(SystemErrorCode.INVALID_ARGUMENT.getCode(),"请核对原问题和AI请求内容");}
    @GetMapping("/latest") public AgronomyAssistantCoordinator.Latest latest(@PathVariable Long caseId) {return service.latest(caseId);}
    @PostMapping("/end") public AgronomyAssistantCoordinator.EndResult end(@PathVariable Long caseId,@RequestBody AgronomyAssistantCoordinator.Command command) {return service.end(caseId,command);}
    @GetMapping("/capability") public AgronomyAssistantCoordinator.Capability capability(@PathVariable Long caseId) {return service.capability(caseId);}
    @PostMapping("/requests") public AgronomyAssistantCoordinator.View submit(@PathVariable Long caseId,@RequestBody AgronomyAssistantCoordinator.Command command) {return service.submit(caseId,command);}
    @GetMapping("/requests/{requestKey}") public AgronomyAssistantCoordinator.Result result(@PathVariable Long caseId,@PathVariable String requestKey) {return service.result(caseId,requestKey);}
}
