package com.agri_system.application.config;

import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.transaction.TransactionContext;
import com.mybatisflex.core.transaction.TransactionalManager;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class JdbcAccessConfigTest {
    @Test
    void isolationFailureThroughFlexProxyReleasesTransactionAndConnection() throws Exception {
        var original = new org.h2.jdbcx.JdbcDataSource();
        original.setURL("jdbc:h2:mem:isolation_failure_" + java.util.UUID.randomUUID());
        var fail = new AtomicBoolean(true);
        var opened = new ArrayList<Connection>();
        var failing = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException {
                Connection connection = original.getConnection();
                opened.add(connection);
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class[]{Connection.class}, (p, method, args) -> {
                    if (fail.get() && method.getName().equals("setTransactionIsolation")) throw new SQLException("测试隔离设置失败");
                    try { return method.invoke(connection, args); }
                    catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
                });
            }
            @Override public Connection getConnection(String user, String password) throws SQLException { return getConnection(); }
        };
        var flex = new FlexDataSource("test", failing, false);
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), flex));
        var manager = new JdbcAccessConfig().transactionManager(new SqlSessionFactoryBuilder().build(configuration));
        var tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            var error = assertThrows(CannotCreateTransactionException.class, () -> tx.execute(status -> {
                throw new AssertionError("事务未建立时不能进入业务写入");
            }));
            assertEquals("测试隔离设置失败", error.getMostSpecificCause().getMessage());
            assertNull(TransactionContext.getXID(), "失败事务不能留在当前工作线程");
            assertTrue(opened.stream().allMatch(connection -> {
                try { return connection.isClosed(); } catch (SQLException errorChecking) { throw new IllegalStateException(errorChecking); }
            }), "失败时必须释放实际连接");
            fail.set(false);
            tx.execute(status -> {
                try (var statement = flex.getConnection().createStatement(); var rows = statement.executeQuery("SELECT 1")) {
                    assertTrue(rows.next());
                    assertEquals(1, rows.getInt(1));
                    return null;
                } catch (SQLException errorChecking) { throw new IllegalStateException(errorChecking); }
            });
            assertNull(TransactionContext.getXID(), "同线程后续正常事务应独立完成");
        } finally {
            if (TransactionContext.getXID() != null) TransactionalManager.rollback(TransactionContext.getXID());
        }
    }
}
