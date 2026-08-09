package com.agri_system.domain.performance.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceLogStatusTest {
    @Test
    void codesAndRoundTrip() {
        assertEquals(1, PerformanceLogStatus.FROZEN.getCode());
        assertEquals(2, PerformanceLogStatus.UNFROZEN.getCode());
        assertEquals(PerformanceLogStatus.FROZEN, PerformanceLogStatus.fromCode(1));
        assertEquals(PerformanceLogStatus.UNFROZEN, PerformanceLogStatus.fromCode(2));
    }

    @Test
    void unknownCodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> PerformanceLogStatus.fromCode(9));
    }
}
