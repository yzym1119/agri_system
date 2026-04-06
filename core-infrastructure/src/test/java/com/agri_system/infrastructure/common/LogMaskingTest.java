package com.agri_system.infrastructure.common;

import com.agri_system.infrastructure.common.log.LogMaskingUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskingTest {

    @Test
    void testPasswordMasking() {
        String payload = "{\"username\":\"user\",\"password\":\"123456\",\"token\":\"abcdef\"}";
        String masked = LogMaskingUtils.mask(payload);
        assertFalse(masked.contains("123456"));
        assertTrue(masked.contains("\"password\":\"******\""));
        assertTrue(masked.contains("\"token\":\"******\""));
    }

    @Test
    void testMobileMasking() {
        String payload = "{\"mobile\":\"13800138000\"}";
        String masked = LogMaskingUtils.mask(payload);
        assertTrue(masked.contains("138****8000"));
    }
}






