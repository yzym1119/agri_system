package com.agri_system.application.performance.event;

import java.math.BigDecimal;

public record CustomerRechargeEvent(Long customerId,
                                    BigDecimal amount,
                                    Long guideStaffId) {
}

