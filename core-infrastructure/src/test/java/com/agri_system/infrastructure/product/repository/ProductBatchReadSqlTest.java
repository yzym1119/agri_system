package com.agri_system.infrastructure.product.repository;

import com.agri_system.infrastructure.product.mapper.ProductMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** 批量目录读取仍使用真实Flex的ID约束和逻辑删除过滤。 */
class ProductBatchReadSqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private ProductRepositoryImpl repository;
    private TenantFactory originalTenant;
    @BeforeEach void setup() {
        originalTenant=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:product_batch_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(source);
        jdbc.execute("""
            CREATE TABLE product(id BIGINT PRIMARY KEY,code VARCHAR(50),barcode VARCHAR(50),name VARCHAR(100),category_id BIGINT,
            specification VARCHAR(100),dosage_form VARCHAR(50),manufacturer VARCHAR(100),registration_no VARCHAR(100),main_unit VARCHAR(20),aux_unit VARCHAR(20),
            main_to_aux_ratio DECIMAL(18,4),suggested_retail_price DECIMAL(18,4),vip_price DECIMAL(18,4),min_price DECIMAL(18,4),avg_cost_price DECIMAL(18,4),
            image_url VARCHAR(200),detail_text VARCHAR(200),is_online INT,base_shipping_fee DECIMAL(18,4),allow_special_fund INT,status INT,deleted INT,
            remark VARCHAR(200),storage_conditions VARCHAR(200),shelf_life_days INT,usage_method VARCHAR(200),min_stock DECIMAL(18,4),max_stock DECIMAL(18,4),reorder_quantity DECIMAL(18,4),
            created_at TIMESTAMP,updated_at TIMESTAMP,created_by BIGINT,updated_by BIGINT)
            """);
        jdbc.execute("INSERT INTO product(id,name,deleted) VALUES(1,'有效商品',0),(2,'删除商品',1),(3,'非请求商品',0)");
        var config=new FlexConfiguration(new Environment("product-batch",new JdbcTransactionFactory(),new FlexDataSource("product-batch",source,false)));
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);config.addMapper(ProductMapper.class);
        session=new FlexSqlSessionFactoryBuilder().build(config).openSession(true);
        repository=new ProductRepositoryImpl(session.getMapper(ProductMapper.class));
    }
    @AfterEach void close() {
        TenantManager.setTenantFactory(originalTenant);if(session!=null)session.close();if(jdbc!=null)jdbc.execute("SHUTDOWN");
    }
    @Test void batchQueryDoesNotReturnDeletedOrUnrequestedProductsAndRefreshesNextRead() {
        var first=repository.findByIds(List.of(1L,2L,999L));assertEquals(1,first.size());assertEquals("有效商品",first.get(0).getName());
        assertTrue(repository.findById(2L).isEmpty());
        jdbc.update("UPDATE product SET name='更新名称' WHERE id=1");
        assertEquals("更新名称",repository.findByIds(List.of(1L)).get(0).getName());
    }
    @Test void emptyBatchDoesNotIssueAnUnboundedCatalogQuery() {
        jdbc.execute("DROP TABLE product");
        assertTrue(repository.findByIds(List.of()).isEmpty());assertTrue(repository.findByIds(null).isEmpty());
    }
}
