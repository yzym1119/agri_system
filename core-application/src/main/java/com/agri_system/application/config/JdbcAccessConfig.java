package com.agri_system.application.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
