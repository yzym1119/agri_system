package com.agri_system.application.report.service;

import com.agri_system.application.report.dto.SalesTrendVO;
import com.agri_system.infrastructure.order.mapper.OrderItemBatchAllocationMapper;
import com.agri_system.infrastructure.order.mapper.OrderItemMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.agri_system.infrastructure.order.repository.OrderItemBatchAllocationRepositoryImpl;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 真实 Flex/H2 SQL：原单日净销、原批次成本及完成售后，避免 Mock 隐藏关联错误。 */
class SalesTrendReturnsSqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private ReportAnalysisService service;
    private TenantFactory originalTenant;
    private final LocalDateTime created = LocalDateTime.of(2026, 10, 1, 12, 0);

    @BeforeEach
    void setUp() {
        originalTenant = TenantManager.getTenantFactory();
        TenantManager.setTenantFactory(null);
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:trend_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE `order` (id BIGINT PRIMARY KEY, store_id BIGINT, order_status INT, pay_amount DECIMAL(18,2), created_at TIMESTAMP, deleted INT DEFAULT 0, order_no VARCHAR(50), customer_id BIGINT, order_type INT, total_amount DECIMAL(18,2), discount_amount DECIMAL(18,2), coupon_amount DECIMAL(18,2), payment_method INT, staff_id BIGINT, receiver_name VARCHAR(100), delivery_phone VARCHAR(50), delivery_address VARCHAR(200), pickup_code VARCHAR(100), remark VARCHAR(200), updated_at TIMESTAMP, created_by BIGINT)");
        jdbc.execute("CREATE TABLE order_item (id BIGINT PRIMARY KEY, order_id BIGINT, product_id BIGINT, package_id BIGINT, quantity DECIMAL(18,4), unit_price DECIMAL(18,12), total_price DECIMAL(18,2), cost_price_snapshot DECIMAL(18,4), package_group_no INT, package_name VARCHAR(100), package_quantity DECIMAL(18,4), package_unit_price DECIMAL(18,2), item_type INT, product_name VARCHAR(100), unit VARCHAR(50), base_unit_id BIGINT, base_quantity DECIMAL(18,4), unit_cost DECIMAL(18,4), commission_amount DECIMAL(18,2), allow_special_fund INT, sort_order INT)");
        jdbc.execute("CREATE TABLE after_sales (id BIGINT PRIMARY KEY, order_id BIGINT, store_id BIGINT, status INT, refund_amount DECIMAL(18,2), refund_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE after_sales_item (id BIGINT PRIMARY KEY, after_sales_id BIGINT, order_item_id BIGINT, product_id BIGINT, quantity DECIMAL(18,4), refund_amount DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE order_item_batch_allocation (id BIGINT PRIMARY KEY, order_item_id BIGINT, store_id BIGINT, product_id BIGINT, batch_id BIGINT, location_id BIGINT, allocated_base_quantity DECIMAL(18,4), batch_unit_cost DECIMAL(18,4), allocated_cost DECIMAL(18,4), evidence_level VARCHAR(50), source_movement_id BIGINT)");
        jdbc.execute("CREATE TABLE inventory_movement (id BIGINT PRIMARY KEY, store_id BIGINT, product_id BIGINT, batch_id BIGINT, from_location_id BIGINT, to_location_id BIGINT, movement_type VARCHAR(30), base_quantity DECIMAL(18,4), reference_type VARCHAR(30), reference_id BIGINT)");
        var config = new FlexConfiguration(new Environment("trend", new JdbcTransactionFactory(), new FlexDataSource("trend", dataSource, false)));
        config.setMapUnderscoreToCamelCase(true);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        config.addMapper(OrderMapper.class);
        config.addMapper(OrderItemMapper.class);
        config.addMapper(OrderItemBatchAllocationMapper.class);
        session = new FlexSqlSessionFactoryBuilder().build(config).openSession(true);
        service = new ReportAnalysisService(session.getMapper(OrderMapper.class), session.getMapper(OrderItemMapper.class),
                null, null, null, null, null,
                new OrderItemBatchAllocationRepositoryImpl(session.getMapper(OrderItemBatchAllocationMapper.class)));
    }

    @AfterEach
    void tearDown() {
        TenantManager.setTenantFactory(originalTenant);
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void completedReturnsReduceOriginalOrderDayRevenueAndOriginalBatchCost() {
        order(1, 1, 9, "65");
        item(11, 1, "1", "25", "25", null); allocation(11, 1, "1", "5");
        item(12, 1, "1", "40", "40", null); allocation(12, 1, "1", "20");
        order(2, 1, 9, "100");
        item(22, 2, "1", "80", "80", null); allocation(22, 1, "1", "50");
        item(23, 2, "1", "20", "20", null); allocation(23, 1, "1", "10");
        completed(1, 1, 11, "1", "25", 50, 1); returned(11, 1, "1");
        completed(2, 2, 22, "1", "80", 50, 1); returned(22, 1, "1");
        var trend = trend(1L);
        money("60", trend.getSaleAmount()); money("30", trend.getCostAmount()); money("30", trend.getProfit());
        money("30", trend.getActualCostAmount());
        assertEquals(created.toLocalDate(), trend.getDate()); // 退款发生次月，仍回原单创建日
    }

    @Test
    void packageUsesAuthoritativeSubtotalAndNetPaidOnceIncludingZeroPriceReturnedMaterials() {
        order(1, 1, 9, "65");
        item(11, 1, "3", "21.666666666667", "70", null); item(12, 1, "1", "0", "0", null);
        jdbc.update("UPDATE order_item SET package_id=99");
        allocation(11, 1, "3", "10"); allocation(12, 1, "1", "5");
        completed(1, 1, 11, "1", "21.67", 50, 1); returned(11, 1, "1");
        completed(2, 1, 12, "1", "0", 50, 1); returned(12, 1, "1");
        var trend = trend(1L);
        money("43.33", trend.getSaleAmount()); money("20", trend.getCostAmount()); money("23.33", trend.getProfit());
    }

    @Test
    void incompleteAndForeignReturnsDoNotReduceSalesOrOriginalCosts() {
        order(1, 1, 6, "100"); item(11, 1, "10", "10", "100", null); allocation(11, 1, "10", "5");
        completed(1, 1, 11, "2", "20", 10, 1);
        completed(2, 1, 11, "2", "20", 40, 1);
        completed(3, 1, 11, "2", "20", 90, 1);
        completed(4, 1, 11, "2", "20", 50, 2);
        returned(11, 2, "2"); returned(11, 1, "-2");
        money("100", trend(1L).getSaleAmount()); money("50", trend(1L).getActualCostAmount());
    }

    @Test
    void fullReturnKeepsDateAndZeroAmountsAndStoreTenantBoundary() {
        order(1, 1, 8, "20"); item(11, 1, "2", "10", "20", null); allocation(11, 1, "2", "5");
        completed(1, 1, 11, "2", "20", 50, 1); returned(11, 1, "2");
        order(2, 2, 6, "900"); item(22, 2, "1", "900", "900", null); allocation(22, 2, "1", "400");
        var trend = trend(1L); money("0", trend.getSaleAmount()); money("0", trend.getCostAmount());
        TenantManager.setTenantFactory(() -> new Object[]{1L});
        money("0", trend(null).getSaleAmount());
        assertEquals(0, service.salesTrend(created.minusDays(1), created.plusDays(1), 2L).size());
    }

    @Test
    void historicalSnapshotAndMissingCostsStaySeparateAfterPartialReturn() {
        order(1, 1, 9, "100"); item(11, 1, "2", "30", "60", "10"); item(12, 1, "2", "20", "40", null);
        completed(1, 1, 11, "1", "30", 50, 1); completed(2, 1, 12, "1", "20", 50, 1);
        var trend = trend(1L);
        money("50", trend.getSaleAmount()); money("10", trend.getEstimatedCostAmount());
        money("30", trend.getEstimatedSaleAmount()); money("20", trend.getCostUnavailableSaleAmount());
        money("0", trend.getActualCostAmount()); money("0", trend.getActualSaleAmount());
    }

    @Test
    void historicalRefundWithoutMatchingDetailsKeepsAuthoritativeNetSaleAsCostUnavailable() {
        order(1, 1, 9, "100"); item(11, 1, "10", "10", "100", null); allocation(11, 1, "10", "5");
        jdbc.update("INSERT INTO after_sales VALUES (1,1,1,50,20,?)", created.plusMonths(1));
        var trend = Assertions.assertDoesNotThrow(() -> trend(1L));
        money("80", trend.getSaleAmount()); money("80", trend.getCostUnavailableSaleAmount());
        money("0", trend.getActualSaleAmount()); money("0", trend.getActualCostAmount());
    }

    @Test
    void completedReturnWithoutOriginalSaleReturnEvidenceMarksCostUnknownWithoutGuessing() {
        order(1, 1, 9, "100"); item(11, 1, "10", "10", "100", null); allocation(11, 1, "10", "5");
        completed(1, 1, 11, "2", "20", 50, 1); returned(11, 1, "2");
        jdbc.update("UPDATE inventory_movement SET batch_id=999 WHERE movement_type='RETURN'");
        var trend = trend(1L);
        money("80", trend.getSaleAmount()); money("80", trend.getCostUnavailableSaleAmount());
        money("0", trend.getActualCostAmount());
        Assertions.assertFalse(trend.getCostWarnings().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"7,60,40", "1,70,30", "-1,10,90", "1,-10,110", "6,110,-10"})
    void invalidCumulativeLineQuantityOrRefundKeepsParentNetButMakesEntireOrderCostUnknown(
            String returnedQuantity, String refund, String netSale) {
        order(1, 1, 9, "100");
        item(11, 1, "6", "10", "60", null); allocation(11, 1, "6", "5");
        item(12, 1, "4", "10", "40", null); allocation(12, 1, "4", "5");
        completed(1, 1, 11, returnedQuantity, refund, 50, 1); returned(11, 1, returnedQuantity);
        var trend = trend(1L);
        money(netSale, trend.getSaleAmount()); money(netSale, trend.getCostUnavailableSaleAmount());
        money("0", trend.getActualCostAmount()); money("0", trend.getActualSaleAmount());
        assertEquals("待核对", trend.getCostStatus());
        Assertions.assertFalse(trend.getCostWarnings().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"-1,10,2,10,1,80", "1,-10,1,20,2,90"})
    void negativeCompletedFactsCannotBeHiddenByPositiveFactsInSameLine(
            String firstQuantity, String firstRefund, String secondQuantity, String secondRefund,
            String returnedQuantity, String netSale) {
        order(1, 1, 9, "100"); item(11, 1, "10", "10", "100", null); allocation(11, 1, "10", "5");
        completed(1, 1, 11, firstQuantity, firstRefund, 50, 1);
        completed(2, 1, 11, secondQuantity, secondRefund, 50, 1); returned(11, 1, returnedQuantity);
        var trend = trend(1L);
        money(netSale, trend.getSaleAmount()); money(netSale, trend.getCostUnavailableSaleAmount());
        money("0", trend.getActualCostAmount()); assertEquals("待核对", trend.getCostStatus());
    }

    @Test
    void negativeParentRefundCannotBeHiddenWhenAggregateParentAndChildrenStillMatch() {
        order(1, 1, 9, "100"); item(11, 1, "10", "10", "100", null); allocation(11, 1, "10", "5");
        completed(1, 1, 11, "1", "0", 50, 1);
        completed(2, 1, 11, "1", "10", 50, 1); returned(11, 1, "2");
        jdbc.update("UPDATE after_sales SET refund_amount=-10 WHERE id=1");
        jdbc.update("UPDATE after_sales SET refund_amount=20 WHERE id=2");
        var trend = trend(1L);
        money("90", trend.getSaleAmount()); money("90", trend.getCostUnavailableSaleAmount());
        money("0", trend.getActualCostAmount()); assertEquals("待核对", trend.getCostStatus());
    }

    private SalesTrendVO trend(Long store) {
        var rows = service.salesTrend(created.minusDays(1), created.plusDays(1), store);
        assertEquals(1, rows.size(), "有原销售记录的日期应保留，含整单完成退货");
        return rows.get(0);
    }
    private void order(long id, long store, int status, String paid) {
        jdbc.update("INSERT INTO `order` (id,store_id,order_status,pay_amount,created_at) VALUES (?,?,?,?,?)", id, store, status, new BigDecimal(paid), created);
    }
    private void item(long id, long order, String qty, String price, String total, String cost) {
        jdbc.update("INSERT INTO order_item (id,order_id,product_id,quantity,unit_price,total_price,cost_price_snapshot) VALUES (?,?,11,?,?,?,?)", id, order, new BigDecimal(qty), new BigDecimal(price), new BigDecimal(total), cost == null ? null : new BigDecimal(cost));
    }
    private void allocation(long item, long store, String qty, String cost) {
        jdbc.update("INSERT INTO order_item_batch_allocation VALUES (?,?,?,11,21,31,?,?,?,'RULE_ALLOCATED',?)", item, item, store, new BigDecimal(qty), new BigDecimal(cost), new BigDecimal(qty).multiply(new BigDecimal(cost)), item + 100);
        jdbc.update("INSERT INTO inventory_movement VALUES (?,?,11,21,31,NULL,'SALE',?,'ORDER',(SELECT order_id FROM order_item WHERE id=?))", item + 100, store, new BigDecimal(qty), item);
    }
    private void completed(long id, long order, long item, String qty, String refund, int status, long store) {
        jdbc.update("INSERT INTO after_sales VALUES (?,?,?,?,?,?)", id, order, store, status, new BigDecimal(refund), created.plusMonths(1));
        jdbc.update("INSERT INTO after_sales_item VALUES (?,?,?,11,?,?)", id, id, item, new BigDecimal(qty), new BigDecimal(refund));
    }
    private void returned(long allocation, long store, String qty) {
        jdbc.update("INSERT INTO inventory_movement VALUES (?,?,11,21,NULL,31,'RETURN',?,'SALE_ALLOCATION',?)", allocation * 1000 + store, store, new BigDecimal(qty), allocation);
    }
    private static void money(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual), "应为" + expected + "，实际" + actual); }
}
