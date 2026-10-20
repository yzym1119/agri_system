package com.agri_system.domain.product.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuantityGranularityPolicyTest {
    @Test void builtInMeasurementLabelsAcceptFractionalBaseQuantity() {
        for (String name : List.of("千克(kg)", "克(g)", "毫升(ml)", "升(L)")) {
            assertTrue(QuantityGranularityPolicy.allowsFractionalBaseQuantity(name), name);
            assertEquals(new BigDecimal("0.50000000"),
                    QuantityGranularityPolicy.requireBaseQuantity(name, new BigDecimal("0.5")), name);
        }
    }

    @Test void packagingAndUnknownDecoratedUnitsRemainIndivisible() {
        for (String name : List.of("袋", "瓶", "箱", "袋(kg)", "瓶(ml)", "箱(L)", "自定义(kg)", "千克(袋)", "公斤装")) {
            assertFalse(QuantityGranularityPolicy.allowsFractionalBaseQuantity(name), name);
            assertThrows(IllegalArgumentException.class,
                    () -> QuantityGranularityPolicy.requireBaseQuantity(name, new BigDecimal("0.5")), name);
        }
    }

    @Test void halfBoxOnlyWorksWhenConvertedBasePiecesAreWhole() {
        assertEquals(new BigDecimal("6.00000000"),
                QuantityGranularityPolicy.requireBaseQuantity("瓶", new BigDecimal("6")));
        assertThrows(IllegalArgumentException.class,
                () -> QuantityGranularityPolicy.requireBaseQuantity("瓶", new BigDecimal("1.2")));
    }
}
