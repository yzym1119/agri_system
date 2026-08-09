package com.agri_system.domain.performance.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class StaffPerformanceLogFreezeTest {
    @Test
    void markFrozenSetsFrozenStateAndFutureUntil() {
        StaffPerformanceLog log = new StaffPerformanceLog();
        LocalDateTime before = LocalDateTime.now();
        log.markFrozen(15);
        assertEquals(PerformanceLogStatus.FROZEN.getCode(), log.getStatus());
        assertNotNull(log.getFreezeUntil());
        assertTrue(log.getFreezeUntil().isAfter(before.plusDays(14)));
    }

    @Test
    void markImmediateSetsUnfrozenStateAndNowUntil() {
        StaffPerformanceLog log = new StaffPerformanceLog();
        LocalDateTime before = LocalDateTime.now();
        log.markImmediate();
        assertEquals(PerformanceLogStatus.UNFROZEN.getCode(), log.getStatus());
        assertNotNull(log.getFreezeUntil());
        assertFalse(log.getFreezeUntil().isBefore(before));
    }
}
