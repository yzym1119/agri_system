package com.agri_system.application.performance.event;

public record CustomerFirstEffectiveEvent(Long customerId,
                                          Long staffId,
                                          Long orderId) {
}

