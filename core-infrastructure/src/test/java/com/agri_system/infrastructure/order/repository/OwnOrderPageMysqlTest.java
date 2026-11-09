package com.agri_system.infrastructure.order.repository;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

/** 与H2共用同一真实Mapper/分页断言；仅本次CREATE成功的随机库可删除。 */
@EnabledIfSystemProperty(named="mysql.it",matches="true")
class OwnOrderPageMysqlTest extends OwnOrderPageSqlTest {
    private JdbcTemplate server;
    private String schema;
    private boolean created;
    @Override protected DataSource dataSource() {
        String host=System.getProperty("mysql.it.host","127.0.0.1");
        String port=System.getProperty("mysql.it.port","3306");
        String user=System.getProperty("mysql.it.user","root");
        String password=System.getProperty("mysql.it.password","");
        String params="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String base="jdbc:mysql://"+host+":"+port+"/";
        server=new JdbcTemplate(new DriverManagerDataSource(base+params,user,password));
        schema="agri_own_order_it_"+java.util.UUID.randomUUID().toString().replace("-","");
        server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");created=true;
        return new DriverManagerDataSource(base+schema+params,user,password);
    }
    @Override protected void releaseDataSource(){if(created){server.execute("DROP DATABASE `"+schema+"`");created=false;}}
}
