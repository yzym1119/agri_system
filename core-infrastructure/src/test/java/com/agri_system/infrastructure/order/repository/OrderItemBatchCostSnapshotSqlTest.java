package com.agri_system.infrastructure.order.repository;

import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import com.mybatisflex.spring.FlexTransactionManager;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 固定成本展示快照的真实SQL副作用，不能用整行保存覆盖成交或历史成本。 */
class OrderItemBatchCostSnapshotSqlTest {
    private JdbcTemplate jdbc;
    private com.agri_system.domain.order.repository.OrderItemRepository repository;
    private TenantFactory originalTenant;

    @BeforeEach
    void setup() {
        originalTenant = TenantManager.getTenantFactory();
        TenantManager.setTenantFactory(null);
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:cost_snapshot_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var flex = new FlexDataSource("cost-snapshot", source, false);
        jdbc = new JdbcTemplate(flex);
        jdbc.execute("CREATE TABLE order_item(id BIGINT PRIMARY KEY,order_id BIGINT,product_id BIGINT,quantity DECIMAL(18,4),unit VARCHAR(20),unit_price DECIMAL(18,2),total_price DECIMAL(18,2),commission_amount DECIMAL(18,2),cost_price_snapshot DECIMAL(18,8),base_unit_id BIGINT,base_quantity DECIMAL(18,4),unit_cost DECIMAL(18,8))");
        jdbc.execute("INSERT INTO order_item VALUES(1,11,21,2,'箱',100,185,7,50,31,24,4),(2,12,21,3,'瓶',10,30,1,5,31,3,5)");
        var config = new FlexConfiguration(new Environment("cost-snapshot", new SpringManagedTransactionFactory(), flex));
        config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.addMapper(OrderItemMapper.class);
        var mapper = new SqlSessionTemplate(new FlexSqlSessionFactoryBuilder().build(config)).getMapper(OrderItemMapper.class);
        repository = new OrderItemRepositoryImpl(mapper, jdbc);
    }

    @AfterEach
    void close() {
        TenantManager.setTenantFactory(originalTenant);
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void 只改目标行三列并保留成交折扣提成与历史成本() {
        Map<String, Object> before = row(1), other = row(2);
        assertEquals(1, update(1L, 32L, "2.5", "62.4"));
        var after = row(1);
        assertEquals(32L, after.get("BASE_UNIT_ID"));
        assertEquals(0, new BigDecimal("2.5").compareTo((BigDecimal) after.get("BASE_QUANTITY")));
        assertEquals(0, new BigDecimal("62.4").compareTo((BigDecimal) after.get("UNIT_COST")));
        before.keySet().removeAll(java.util.Set.of("BASE_UNIT_ID", "BASE_QUANTITY", "UNIT_COST"));
        after.keySet().removeAll(java.util.Set.of("BASE_UNIT_ID", "BASE_QUANTITY", "UNIT_COST"));
        assertEquals(before, after);
        assertEquals(other, row(2));
    }

    @Test
    void 空展示成本可清除旧值且不存在的明细不插入() {
        assertEquals(1, update(1L, 31L, "24", null));
        assertNull(row(1).get("UNIT_COST"));
        assertEquals(0, update(999L, 31L, "24", "4"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM order_item", Integer.class));
    }

    @Test
    void 同Flex事务中快照与伴随写入一起回滚() {
        var before = row(1);
        var tx = new TransactionTemplate(new FlexTransactionManager());
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            update(1L, 32L, "2.5", "62.4");
            jdbc.update("UPDATE order_item SET commission_amount=99 WHERE id=1");
            assertEquals(32L, row(1).get("BASE_UNIT_ID"));
            throw new IllegalStateException("模拟出库事务后续失败");
        }));
        assertEquals(before, row(1));
    }

    private int update(Long id, Long baseUnitId, String quantity, String cost) {
        return repository.updateBatchCostSnapshot(id, baseUnitId, new BigDecimal(quantity), cost == null ? null : new BigDecimal(cost));
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM order_item WHERE id=?", id);
    }
}
