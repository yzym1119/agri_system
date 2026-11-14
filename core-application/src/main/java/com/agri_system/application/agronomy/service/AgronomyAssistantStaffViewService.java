package com.agri_system.application.agronomy.service;

import java.util.Objects;

/**
 * 员工/后台只读AI参考视图：先沿原农技问题的员工范围校验，再复用客户同一白名单投影。
 * 不派出、不结束、不预占，也不把未审核草案、全文、哈希、许可或费用交给页面。
 */
public class AgronomyAssistantStaffViewService {
    private final AgronomyCaseService cases;
    private final AgronomyAssistantCoordinator coordinator;
    public AgronomyAssistantStaffViewService(AgronomyCaseService cases, AgronomyAssistantCoordinator coordinator) {
        this.cases = Objects.requireNonNull(cases); this.coordinator = Objects.requireNonNull(coordinator);
    }
    public AgronomyAssistantCoordinator.Latest latest(Long staffId, Long caseId) {
        return coordinator.latestFor(cases.staffCase(staffId, caseId));
    }
}
