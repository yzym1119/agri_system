package com.agri_system.infrastructure.order.query;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 提货证据查询的真实 SQL 基线：从应用服务迁入后列集合、筛选与参数顺序不变。 */
class PickupEvidenceQuerySqlTest {
    private JdbcTemplate jdbc;
    private PickupEvidenceQueryImpl query;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:pickup_evidence_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE product_unit(id BIGINT,product_id BIGINT,unit_name VARCHAR(50),is_base_unit INT)");
        jdbc.execute("CREATE TABLE inventory_movement(id BIGINT PRIMARY KEY,store_id BIGINT,product_id BIGINT,batch_id BIGINT,"
                + "from_location_id BIGINT,base_quantity DECIMAL(20,4),movement_type VARCHAR(20),reference_type VARCHAR(30),reference_id BIGINT)");
        jdbc.execute("CREATE TABLE after_sales(id BIGINT PRIMARY KEY,order_id BIGINT,status INT)");
        query = new PickupEvidenceQueryImpl(jdbc);
    }

    @Test
    void findsOnlyTheSingleEnabledBaseUnitNameOfTheSameProduct() {
        jdbc.update("INSERT INTO product_unit VALUES(7,99,'瓶',1)");
        jdbc.update("INSERT INTO product_unit VALUES(8,99,'箱',0)");
        assertEquals("瓶", query.findBaseUnitName(7L, 99L));
        assertNull(query.findBaseUnitName(8L, 99L));       // 非基准单位
        assertNull(query.findBaseUnitName(7L, 100L));      // 商品不匹配
        assertNull(query.findBaseUnitName(999L, 99L));     // 不存在
        jdbc.update("INSERT INTO product_unit VALUES(7,99,'瓶',1)");
        assertNull(query.findBaseUnitName(7L, 99L));       // 重复行不猜取第一条
    }

    @Test
    void batchReadsOriginalSaleFactsPositiveReturnsAndProcessingAfterSalesFlag() {
        sale(1001L, 101L, 50L);
        sale(1002L, 102L, 51L);
        returned(2001L, 1L, new BigDecimal("0.5"));
        returned(2002L, 2L, BigDecimal.ZERO);       // 零量不参与累计
        returned(2003L, 3L, new BigDecimal("-1"));  // 负量不参与累计
        jdbc.update("INSERT INTO after_sales VALUES(1,50,10)");     // 处理中售后
        jdbc.update("INSERT INTO after_sales VALUES(2,51,50)");     // 已完成售后不算处理中

        List<Map<String, Object>> rows = query.findMovements(List.of(1001L, 1002L), List.of(1L, 2L, 3L));
        assertEquals(3, rows.size());
        Map<String, Object> firstSale = row(rows, 1001L);
        for (String column : List.of("store_id", "product_id", "batch_id", "from_location_id",
                "base_quantity", "movement_type", "reference_type", "reference_id", "processing_after_sales")) {
            assertTrue(firstSale.containsKey(column), "缺少原 SELECT m.* 列: " + column);
        }
        assertEquals(1L, number(firstSale.get("processing_after_sales")));
        assertEquals(0L, number(row(rows, 1002L).get("processing_after_sales")));
        assertEquals("RETURN", row(rows, 2001L).get("movement_type"));
        assertTrue(rows.stream().noneMatch(r -> number(r.get("id")) == 2002L || number(r.get("id")) == 2003L));
    }

    @Test
    void emptySourceIdsStillReadsPositiveReturnsWithoutBuildingIllegalInClause() {
        returned(2001L, 1L, BigDecimal.ONE);
        List<Map<String, Object>> rows = query.findMovements(List.of(), List.of(1L));
        assertEquals(1, rows.size());
        assertEquals(2001L, number(rows.get(0).get("id")));
    }

    @Test
    void emptyAllocationIdsShortCircuits() {
        assertTrue(query.findMovements(List.of(1001L), List.of()).isEmpty());
    }

    private void sale(Long id, Long batch, Long order) {
        jdbc.update("INSERT INTO inventory_movement VALUES(?,3,99,?,12,1,'SALE','ORDER',?)", id, batch, order);
    }

    private void returned(Long id, Long allocation, BigDecimal quantity) {
        jdbc.update("INSERT INTO inventory_movement VALUES(?,3,99,101,NULL,?,'RETURN','SALE_ALLOCATION',?)",
                id, quantity, allocation);
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, long id) {
        return rows.stream().filter(r -> number(r.get("id")) == id).findFirst().orElseThrow();
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }
}
