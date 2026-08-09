package com.agri_system.application.performance.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformancePropertiesDefaultsTest {
    @Test
    void freezeDefaults() {
        PerformanceProperties p = new PerformanceProperties();
        assertFalse(p.isFreezeEnabled(), "冻结默认关闭(灰度安全)");
        assertEquals(15, p.getFreezeDays());
    }
}
