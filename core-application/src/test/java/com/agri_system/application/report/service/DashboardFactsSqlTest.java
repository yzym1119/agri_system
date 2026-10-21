package com.agri_system.application.report.service;

import com.agri_system.application.report.dto.DashboardVO;
import com.agri_system.infrastructure.after_sales.mapper.AfterSalesMapper;
import com.agri_system.infrastructure.credit.mapper.CreditAccountMapper;
import com.agri_system.infrastructure.credit.mapper.CreditRepaymentLogMapper;
import com.agri_system.infrastructure.customer.mapper.CustomerMapper;
import com.agri_system.infrastructure.inventory.mapper.InventoryBatchMapper;
import com.agri_system.infrastructure.order.mapper.OrderMapper;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.core.mybatis.FlexSqlSessionFactoryBuilder;
import com.mybatisflex.core.tenant.TenantFactory;
import com.mybatisflex.core.tenant.TenantManager;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 工作台在真实 Flex Mapper / H2 SQL 上核对成交、欠款和可派物料事实。 */
class DashboardFactsSqlTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private ReportAnalysisService service;
    private TenantFactory originalTenant;
    private final LocalDateTime today = LocalDate.now().atTime(12, 0);

    @BeforeEach
    void setUp() {
        originalTenant = TenantManager.getTenantFactory();
        TenantManager.setTenantFactory(null);
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:dashboard_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE `order` (id BIGINT PRIMARY KEY, store_id BIGINT, order_type INT, order_status INT, payment_method INT DEFAULT 6, delivery_status INT DEFAULT 0, total_amount DECIMAL(18,2), pay_amount DECIMAL(18,2), created_at TIMESTAMP, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE credit_account (id BIGINT PRIMARY KEY, store_id BIGINT, credit_amount DECIMAL(18,2), remaining_amount DECIMAL(18,2), due_date DATE, status INT, created_at TIMESTAMP, paid_amount DECIMAL(18,2) DEFAULT 0)");
        jdbc.execute("CREATE TABLE credit_repayment_log (id BIGINT PRIMARY KEY, store_id BIGINT, amount DECIMAL(18,2), created_at TIMESTAMP, credit_account_id BIGINT, refunded_amount DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE customer (id BIGINT PRIMARY KEY, created_at TIMESTAMP, deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE after_sales (id BIGINT PRIMARY KEY, order_id BIGINT, store_id BIGINT, status INT, refund_amount DECIMAL(18,2))");
        jdbc.execute("CREATE TABLE order_delivery (id BIGINT PRIMARY KEY, order_id BIGINT, delivery_status INT, delivery_type INT)");
        jdbc.execute("CREATE TABLE order_item (id BIGINT PRIMARY KEY, order_id BIGINT, product_id BIGINT)");
        jdbc.execute("CREATE TABLE order_item_batch_allocation (id BIGINT PRIMARY KEY, order_item_id BIGINT, store_id BIGINT, product_id BIGINT, batch_id BIGINT, location_id BIGINT, allocated_base_quantity DECIMAL(18,4), source_movement_id BIGINT)");
        jdbc.execute("CREATE TABLE inventory_movement (id BIGINT PRIMARY KEY, store_id BIGINT, product_id BIGINT, batch_id BIGINT, from_location_id BIGINT, to_location_id BIGINT, movement_type VARCHAR(30), base_quantity DECIMAL(18,4), reference_type VARCHAR(30), reference_id BIGINT)");
        // 库存摘要也执行真实 Mapper；空余额不干扰本组工作台事实。
        jdbc.execute("CREATE TABLE inventory_receipt_batch (id BIGINT, store_id BIGINT, product_id BIGINT, supplier_batch_no VARCHAR(50), production_date DATE, expiry_date DATE, base_unit_cost DECIMAL(18,4), received_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE inventory_batch_balance (batch_id BIGINT, base_quantity DECIMAL(18,4))");
        jdbc.execute("CREATE TABLE product (id BIGINT, main_unit VARCHAR(20))");
        jdbc.execute("CREATE TABLE product_unit (product_id BIGINT, unit_name VARCHAR(20), base_quantity_per_unit DECIMAL(18,4))");
        var configuration = new FlexConfiguration(new Environment("dashboard-test", new JdbcTransactionFactory(), new FlexDataSource("dashboard-test", dataSource, false)));
        configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        for (Class<?> mapper : new Class<?>[]{OrderMapper.class, CreditAccountMapper.class,
                CreditRepaymentLogMapper.class, CustomerMapper.class, AfterSalesMapper.class, InventoryBatchMapper.class}) {
            configuration.addMapper(mapper);
        }
        session = new FlexSqlSessionFactoryBuilder().build(configuration).openSession(true);
        service = new ReportAnalysisService(session.getMapper(OrderMapper.class), null,
                session.getMapper(InventoryBatchMapper.class), session.getMapper(CreditAccountMapper.class),
                session.getMapper(CreditRepaymentLogMapper.class), session.getMapper(CustomerMapper.class), null,
                session.getMapper(AfterSalesMapper.class), null);
    }

    @AfterEach
    void tearDown() {
        TenantManager.setTenantFactory(originalTenant);
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void newCreditUsesExplicitStoreWhenAdministratorHasNoTenantFilter() {
        account(1, 1, 100, 70, 1, LocalDate.now().plusDays(1));
        account(2, 2, 900, 900, 1, LocalDate.now().plusDays(1));
        money("100", service.dashboardToday(1L).getNewCreditAmount());
        money("900", service.dashboardToday(2L).getNewCreditAmount());
        money("1000", service.dashboardToday(null).getNewCreditAmount());
    }

    @Test
    void creditRiskIncludesOverdueStatusThreePositiveBalanceAndKeepsStoreBoundary() {
        account(1, 1, 100, 70, 3, LocalDate.now().minusDays(1));
        account(2, 1, 40, 40, 1, LocalDate.now().plusDays(1));
        account(3, 1, 20, 0, 3, LocalDate.now().minusDays(1));
        account(4, 2, 900, 900, 3, LocalDate.now().minusDays(1));
        account(5, 1, 10, 10, 3, null); // 历史到期日未知，明确逾期状态仍保留
        jdbc.update("INSERT INTO credit_repayment_log (id,store_id,amount,created_at) VALUES (1,1,30,?),(2,2,400,?)", today, today);
        var risk = service.creditRisk(1L);
        money("120", risk.getTotalDebt());
        money("80", risk.getOverdueDebt());
        money("170", risk.getMonthlyNewCredit());
        money("30", risk.getMonthlyRepayment());
        money("1020", service.creditRisk(null).getTotalDebt());
    }

    @Test
    void todayNetSalesSubtractsOnlyCompletedOriginalOrderRefundsAndIncludesCredit() {
        order(1, 1, 9, 100, today);
        account(10, 1, 100, 70, 1, LocalDate.now().plusDays(1));
        jdbc.update("INSERT INTO after_sales VALUES (1,1,1,50,10),(6,1,1,50,20),(2,1,1,10,20),(3,1,1,40,20),(4,1,1,90,20),(5,1,2,50,90)");
        // 原单100，已完成退货30，实收可为0（全赊销），净销售仍为70。
        DashboardVO partial = service.dashboardToday(1L);
        money("70", partial.getNetSales());
        money("100", partial.getGmv());
        assertEquals(1L, partial.getOrderCount());
        order(2, 1, 8, 200, today); // 全退净额0，不进入旧GMV/单数
        order(3, 1, 7, 200, today);
        order(4, 1, 6, 500, today.minusDays(1));
        order(5, 2, 6, 900, today);
        order(6, 1, 6, 100, today); // 全赊销无当日现金收款，也属于成交
        money("170", service.dashboardToday(1L).getNetSales());
        money("200", service.dashboardToday(1L).getGmv());
        assertEquals(2L, service.dashboardToday(1L).getOrderCount());
    }

    @Test
    void pendingDeliveryCountsPosAndPartialReturnOnlyWhilePhysicalGoodsRemain() {
        pending(1, 1, 4, 2);
        pending(2, 1, 9, 2);
        jdbc.update("INSERT INTO order_delivery VALUES (99,1,2,2)"); // 重复关联不把一单算两次
        returned(202, 2, 1, "3");
        jdbc.update("INSERT INTO after_sales VALUES (1,2,1,50,30)");
        // 正数退货才抵减；无关引用及其他门店不能冲掉本单物料。
        returned(203, 2, 1, "-50");
        jdbc.update("INSERT INTO inventory_movement VALUES (204,2,11,21,NULL,31,'RETURN',100,'SALE_ALLOCATION',2)");
        pending(3, 2, 4, 2);
        jdbc.update("UPDATE `order` SET order_type=3 WHERE id=3");
        jdbc.update("UPDATE order_delivery SET delivery_type=3 WHERE order_id=3");
        assertEquals(2L, service.dashboardToday(1L).getPendingDeliveryCount());
        assertEquals(1L, service.dashboardToday(2L).getPendingDeliveryCount());
        assertEquals(3L, service.dashboardToday(null).getPendingDeliveryCount());
    }

    @Test
    void pendingDeliveryExcludesBlockedReturnedCancelledMissingOrInvalidSaleEvidence() {
        for (int id = 1; id <= 20; id++) pending(id, 1, 4, 2);
        jdbc.update("INSERT INTO after_sales VALUES (1,1,1,10,1),(2,2,1,40,1)");
        returned(203, 3, 1, "10");
        jdbc.update("UPDATE `order` SET order_status=7 WHERE id=4");
        jdbc.update("UPDATE `order` SET order_status=8 WHERE id=5");
        jdbc.update("DELETE FROM order_delivery WHERE order_id=6");
        jdbc.update("UPDATE order_delivery SET delivery_status=3 WHERE order_id=7");
        jdbc.update("DELETE FROM order_item_batch_allocation WHERE id=8");
        jdbc.update("DELETE FROM inventory_movement WHERE id=109");
        jdbc.update("UPDATE inventory_movement SET movement_type='TRANSFER' WHERE id=110");
        jdbc.update("UPDATE inventory_movement SET reference_id=999 WHERE id=111");
        jdbc.update("UPDATE inventory_movement SET store_id=2 WHERE id=112");
        jdbc.update("UPDATE inventory_movement SET base_quantity=0 WHERE id=113");
        jdbc.update("UPDATE inventory_movement SET base_quantity=9 WHERE id=114");
        jdbc.update("UPDATE inventory_movement SET product_id=999 WHERE id=115");
        jdbc.update("UPDATE inventory_movement SET batch_id=999 WHERE id=116");
        jdbc.update("UPDATE inventory_movement SET from_location_id=999 WHERE id=117");
        jdbc.update("UPDATE inventory_movement SET reference_type='PURCHASE' WHERE id=118");
        jdbc.update("UPDATE order_delivery SET delivery_type=NULL WHERE order_id=19");
        jdbc.update("UPDATE order_delivery SET delivery_type=99 WHERE order_id=20");
        assertEquals(0L, service.dashboardToday(1L).getPendingDeliveryCount());
    }

    @Test
    void processingAfterSalesBlocksOriginalOrderEvenWithInconsistentStoreReference() {
        pending(1, 1, 4, 2);
        jdbc.update("INSERT INTO after_sales VALUES (1,1,2,10,1)");
        assertEquals(0L, service.dashboardToday(1L).getPendingDeliveryCount());
    }

    @Test
    void explicitStoreAndEmployeeTenantIntersectForDashboardAndCreditRisk() {
        pending(1, 1, 4, 2);
        pending(2, 2, 4, 2);
        account(1, 1, 100, 70, 3, LocalDate.now().minusDays(1));
        account(2, 2, 900, 900, 3, LocalDate.now().minusDays(1));
        TenantManager.setTenantFactory(() -> new Object[]{1L});
        assertEquals(1L, service.dashboardToday(null).getPendingDeliveryCount());
        money("70", service.creditRisk(null).getTotalDebt());
        DashboardVO forbidden = service.dashboardToday(2L);
        assertEquals(0L, forbidden.getPendingDeliveryCount());
        money("0", forbidden.getNewCreditAmount());
        money("0", forbidden.getNetSales());
        money("0", service.creditRisk(2L).getTotalDebt());
        money("0", service.creditRisk(2L).getMonthlyNewCredit());
    }

    @Test
    void monthlyCreditRateUsesSameAccountsAndDoesNotCountWriteOffAsCash() {
        account(1, 1, 290, 140, 1, LocalDate.now().plusDays(1));
        paid(1, 1, 1, 100, "0", today);
        // 原赊290：实还100、退货冲欠50、剩余140，净实还占比应为100/240。
        money("41.67", service.creditRisk(1L).getRepaymentRate());
        jdbc.update("UPDATE credit_account SET remaining_amount=0,status=2 WHERE id=1");
        paid(2, 1, 1, 140, "0", today);
        money("100", service.creditRisk(1L).getRepaymentRate());
        money("290", service.creditRisk(1L).getMonthlyNewCredit());
        money("240", service.creditRisk(1L).getMonthlyRepayment());
    }

    @Test
    void monthlyCreditRateExcludesOldDebtsAndOtherStoresButKeepsMonthlyReceipts() {
        account(1, 1, 200, 100, 1, null);
        paid(1, 1, 1, 100, "0", today);
        account(2, 1, 900, 0, 2, null);
        jdbc.update("UPDATE credit_account SET created_at=? WHERE id=2", LocalDate.now().withDayOfMonth(1).minusDays(1).atStartOfDay());
        paid(2, 1, 2, 900, "0", today);
        account(3, 2, 800, 0, 2, null);
        paid(3, 2, 3, 800, "0", today);
        money("50", service.creditRisk(1L).getRepaymentRate());
        money("1000", service.creditRisk(1L).getMonthlyRepayment());
        TenantManager.setTenantFactory(() -> new Object[]{1L});
        money("50", service.creditRisk(null).getRepaymentRate());
        assertNull(service.creditRisk(2L).getRepaymentRate());
    }

    @Test
    void monthlyCreditRateSubtractsRefundedRepaymentAndDoesNotGuessUnknownHistory() {
        account(1, 1, 200, 100, 1, null);
        paid(1, 1, 1, 100, "40", today);
        money("37.50", service.creditRisk(1L).getRepaymentRate());
        // 其他门店伪关联不能冲掉本店资金。
        jdbc.update("INSERT INTO credit_repayment_log VALUES (2,2,900,?,1,NULL)", today);
        money("37.50", service.creditRisk(1L).getRepaymentRate());
        jdbc.update("UPDATE credit_repayment_log SET refunded_amount=NULL WHERE id=1");
        assertNull(service.creditRisk(1L).getRepaymentRate());
        jdbc.update("UPDATE credit_repayment_log SET refunded_amount=0,credit_account_id=NULL WHERE id=1");
        assertNull(service.creditRisk(1L).getRepaymentRate());
    }

    @Test
    void noMonthlyCreditOrFullyWrittenOffCreditHasNoCashRecoveryRate() {
        assertNull(service.creditRisk(1L).getRepaymentRate());
        account(1, 1, 100, 100, 1, null);
        money("0", service.creditRisk(1L).getRepaymentRate());
        jdbc.update("UPDATE credit_account SET remaining_amount=0,status=2 WHERE id=1");
        assertNull(service.creditRisk(1L).getRepaymentRate());
    }

    private void paid(long id, long store, long account, int amount, String refunded, LocalDateTime date) {
        jdbc.update("INSERT INTO credit_repayment_log VALUES (?,?,?,?,?,?)", id, store, amount, date, account, new BigDecimal(refunded));
        jdbc.update("UPDATE credit_account SET paid_amount=paid_amount+? WHERE id=?", amount, account);
    }

    private void account(long id, long store, int original, int remaining, int status, LocalDate due) {
        jdbc.update("INSERT INTO credit_account (id,store_id,credit_amount,remaining_amount,due_date,status,created_at) VALUES (?,?,?,?,?,?,?)", id, store, original, remaining, due, status, today);
    }

    private void order(long id, long store, int status, int amount, LocalDateTime created) {
        jdbc.update("INSERT INTO `order` (id,store_id,order_type,order_status,total_amount,pay_amount,created_at) VALUES (?,?,1,?,?,?,?)",
                id, store, status, amount, amount, created);
    }

    private void pending(long id, long store, int orderStatus, int deliveryStatus) {
        order(id, store, orderStatus, 100, today);
        jdbc.update("INSERT INTO order_delivery VALUES (?,?,?,2)", id, id, deliveryStatus);
        jdbc.update("INSERT INTO order_item VALUES (?,?,11)", id, id);
        jdbc.update("INSERT INTO order_item_batch_allocation VALUES (?,?,?,11,21,31,10,?)", id, id, store, id + 100);
        jdbc.update("INSERT INTO inventory_movement VALUES (?,?,11,21,31,NULL,'SALE',10,'ORDER',?)", id + 100, store, id);
    }

    private void returned(long movementId, long allocationId, long store, String quantity) {
        jdbc.update("INSERT INTO inventory_movement VALUES (?,?,11,21,NULL,31,'RETURN',?,'SALE_ALLOCATION',?)",
                movementId, store, new BigDecimal(quantity), allocationId);
    }

    private static void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "金额应为 " + expected + "，实际 " + actual);
    }
}
