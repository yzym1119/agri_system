package com.agri_system.application.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import com.mybatisflex.spring.FlexTransactionManager;
import com.mybatisflex.core.transaction.TransactionContext;
import com.mybatisflex.core.transaction.TransactionalManager;

import java.sql.SQLException;

/**
 * MyBatis-Flex 使用包装后的数据源与 FlexTransactionManager。
 * JDBC 必须从同一数据源取得事务连接，否则会绕过 @Transactional，甚至等待自身持有的行锁。
 */
@Configuration(proxyBeanMethods = false)
public class JdbcAccessConfig {
    @Bean
    public JdbcTemplate jdbcTemplate(SqlSessionFactory sqlSessionFactory) {
        return new JdbcTemplate(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource());
    }

    /** Flex 1.9.6 不应用隔离定义；在首次业务查询前设置同一Flex事务连接。 */
    @Bean
    public FlexTransactionManager transactionManager(SqlSessionFactory sqlSessionFactory) {
        var dataSource = sqlSessionFactory.getConfiguration().getEnvironment().getDataSource();
        return new FlexTransactionManager() {
            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
                super.doBegin(transaction, definition);
                if (definition.getIsolationLevel() == TransactionDefinition.ISOLATION_DEFAULT) return;
                try {
                    dataSource.getConnection().setTransactionIsolation(definition.getIsolationLevel());
                } catch (SQLException | RuntimeException failure) {
                    TransactionalManager.rollback(TransactionContext.getXID());
                    throw new CannotCreateTransactionException("无法设置业务事务隔离级别", failure);
                }
            }
        };
    }
}
