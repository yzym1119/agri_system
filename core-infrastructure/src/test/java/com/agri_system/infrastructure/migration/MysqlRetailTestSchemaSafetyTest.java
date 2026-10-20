package com.agri_system.infrastructure.migration;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 不连接数据库：验证显式指定已有库时不执行任何删除。 */
class MysqlRetailTestSchemaSafetyTest {
    @Test
    void existingDatabaseCreationFailureNeverGrantsCleanupOwnership() throws Exception {
        Statement statement = mock(Statement.class);
        String create = "CREATE DATABASE `agri_retail_it_existing` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
        SQLException existing = new SQLException("database exists", "HY000", 1007);
        when(statement.execute(create)).thenThrow(existing);
        var schema = new MysqlRetailFactChainIntegrationTest.FreshSchema("agri_retail_it_existing");

        assertSame(existing, assertThrows(SQLException.class, () -> schema.create(statement)));
        assertFalse(schema.isCreated());
        schema.drop(statement);
        verify(statement).execute(create);
        verifyNoMoreInteractions(statement);
    }

    @Test
    void onlySuccessfullyCreatedDatabaseIsDroppedOnce() throws Exception {
        Statement statement = mock(Statement.class);
        var schema = new MysqlRetailFactChainIntegrationTest.FreshSchema("agri_retail_it_owned");
        schema.create(statement);
        assertTrue(schema.isCreated());
        schema.drop(statement);
        schema.drop(statement);
        assertFalse(schema.isCreated());
        var sequence = inOrder(statement);
        sequence.verify(statement).execute("CREATE DATABASE `agri_retail_it_owned` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        sequence.verify(statement).execute("DROP DATABASE `agri_retail_it_owned`");
        verifyNoMoreInteractions(statement);
    }

    @Test
    void unsafeExternalDatabaseNamesAreRejectedBeforeSql() {
        for (String name : new String[]{"agri_system", "mysql", "agri_retail_it_", "agri_retail_it_x`; DROP DATABASE mysql; --",
                "agri_retail_it_" + "a".repeat(64)}) {
            assertThrows(IllegalArgumentException.class, () -> new MysqlRetailFactChainIntegrationTest.FreshSchema(name), name);
        }
    }

    @Test
    void failedCleanupIsReportedAndRetainsOwnership() throws Exception {
        Statement statement = mock(Statement.class);
        var schema = new MysqlRetailFactChainIntegrationTest.FreshSchema("agri_retail_it_cleanup");
        schema.create(statement);
        SQLException failure = new SQLException("cleanup denied");
        when(statement.execute("DROP DATABASE `agri_retail_it_cleanup`")).thenThrow(failure);
        assertSame(failure, assertThrows(SQLException.class, () -> schema.drop(statement)));
        assertTrue(schema.isCreated());
    }
}
