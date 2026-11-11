package com.agri_system.application.agronomy.service;

import com.agri_system.application.auth.service.AuthService;
import lombok.RequiredArgsConstructor;

/** 客户入口只取真实当前会话，不接受body中自报的客户或授权。保持协调器事务外入口。 */
@RequiredArgsConstructor
public class AgronomyAssistantAccessService {
    private final AuthService auth;
    private final AgronomyAssistantCoordinator coordinator;
    public AgronomyAssistantCoordinator.Latest latest(Long caseId) {
        return coordinator.latest(auth.getCurrentClientAuthorization(),caseId);
    }
    public AgronomyAssistantCoordinator.EndResult end(Long caseId,AgronomyAssistantCoordinator.Command command) {
        return coordinator.end(auth.getCurrentClientAuthorization(),caseId,command);
    }
    public AgronomyAssistantCoordinator.Capability capability(Long caseId) {
        return coordinator.capability(auth.getCurrentClientAuthorization(),caseId);
    }
    public AgronomyAssistantCoordinator.View submit(Long caseId,AgronomyAssistantCoordinator.Command command) {
        return coordinator.submit(auth.getCurrentClientAuthorization(),caseId,command);
    }
    public AgronomyAssistantCoordinator.Result result(Long caseId,String requestKey) {
        return coordinator.customerResult(auth.getCurrentClientAuthorization(),caseId,requestKey);
    }
}
