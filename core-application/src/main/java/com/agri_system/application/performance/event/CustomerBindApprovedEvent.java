package com.agri_system.application.performance.event;

public record CustomerBindApprovedEvent(Long customerId,
                                        Long staffId,
                                        String bindSource,
                                        Long bindOrderId) {
}

