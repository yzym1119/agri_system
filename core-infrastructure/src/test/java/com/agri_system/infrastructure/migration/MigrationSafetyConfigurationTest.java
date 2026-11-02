package com.agri_system.infrastructure.migration;

import com.agri_system.infrastructure.common.config.MigrationSafetyConfiguration;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.*;

class MigrationSafetyConfigurationTest {
    @Test void startupConfigurationExposesTheHistoryGuard() {
        try(var context=new AnnotationConfigApplicationContext(MigrationSafetyConfiguration.class)) {
            Callback guard=context.getBean(Callback.class);
            assertInstanceOf(LegacyPerformanceDropGuard.class,guard);
            assertTrue(guard.supports(Event.BEFORE_MIGRATE,null));
            assertFalse(guard.supports(Event.AFTER_MIGRATE,null));
        }
    }
}
