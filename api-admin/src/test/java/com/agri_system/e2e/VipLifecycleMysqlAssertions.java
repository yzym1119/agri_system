package com.agri_system.e2e;

import com.agri_system.application.customer.service.VipLifecycleService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 历史 VIP 集成用例的自包含替代，仅由一次性 MySQL 业务链调用。
 * 不使用已有客户、不静默跳过，不改生产配置；由调用方销毁整座一次性库。
 */
final class VipLifecycleMysqlAssertions {
    private static final long ACTIVE = 8_700_000_001L;
    private static final long EXPIRED = 8_700_000_002L;
    private static final long NORMAL = 8_700_000_003L;
    private static final long TEMPLATE = 8_700_000_011L;
    private static final long COUPON_RIGHT = 8_700_000_021L;
    private static final long FUND_RIGHT = 8_700_000_022L;
    private static final int LEVEL = 4;
    private static final String COUPON_NAME = "一次性库VIP月券";

    private VipLifecycleMysqlAssertions() {}

    static void verify(JdbcTemplate jdbc, VipLifecycleService service) {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertNotNull(database);
        assertTrue(database.startsWith("agri_unified_inventory_it_"),
                "VIP夹具只允许在零售事实链创建的一次性库运行");
        assertEquals(0L, count(jdbc,
                "SELECT COUNT(*) FROM marketing_vip_rights WHERE vip_level = ?", LEVEL),
                "夹具等级必须独立，已有配置冲突应失败，不能依赖现成权益");

        LocalDate today = LocalDate.now();
        String month = YearMonth.from(today).toString();
        Timestamp validStart = Timestamp.valueOf(today.minusDays(1).atStartOfDay());
        Timestamp validEnd = Timestamp.valueOf(today.plusDays(30).atStartOfDay());
        customer(jdbc, ACTIVE, "有效VIP", 2, today.minusDays(30), today.plusDays(30));
        customer(jdbc, EXPIRED, "过期VIP", 2, today.minusDays(60), today.minusDays(1));
        customer(jdbc, NORMAL, "普通客户", 1, null, null);
        jdbc.update("INSERT INTO coupon_template "
                        + "(id,name,type,amount,min_order_amount,validity_type,valid_start_time,valid_end_time,total_count,status) "
                        + "VALUES (?,?,1,7.50,0,1,?,?,-1,1)", TEMPLATE, COUPON_NAME, validStart, validEnd);
        jdbc.update("INSERT INTO marketing_vip_rights "
                        + "(id,vip_level,benefit_type,template_id,template_count,status) VALUES (?,?,1,?,2,1)",
                COUPON_RIGHT, LEVEL, TEMPLATE);
        jdbc.update("INSERT INTO marketing_vip_rights "
                        + "(id,vip_level,benefit_type,benefit_value,status) VALUES (?,?,2,12.34,1)",
                FUND_RIGHT, LEVEL);
        jdbc.update("INSERT INTO customer_wallet "
                        + "(id,customer_id,wallet_type,balance,frozen_balance,version) VALUES (?,?,2,3.21,0,0)",
                8_700_000_031L, ACTIVE);

        // 非VIP与已过期客户均不得落下任何权益事实。
        Map<String, List<Map<String, Object>>> before = snapshot(jdbc);
        service.grantMonthlyBenefit(EXPIRED, month);
        service.grantMonthlyBenefit(NORMAL, month);
        assertEquals(before, snapshot(jdbc), "过期/普通客户不能领取VIP月权益");

        // 尚无月记录，避免空参数查询条件策略使其误命中另一月份。
        // 月份NOT NULL在权益写入之后失败，必须由真实服务事务回滚先前钱券及流水。
        assertThrows(RuntimeException.class, () -> service.grantMonthlyBenefit(ACTIVE, null),
                "非法月份必须明确失败，不能截断、跳过或伪造成功");
        assertEquals(before, snapshot(jdbc), "最终月记录失败必须回滚本次券、余额、流水和日志");

        service.grantMonthlyBenefit(ACTIVE, month);
        assertEquals(2L, count(jdbc,
                "SELECT COUNT(*) FROM customer_coupon WHERE customer_id=? AND template_id=? "
                        + "AND name=? AND amount=7.50 AND status=1 AND start_time=? AND end_time=?",
                ACTIVE, TEMPLATE, COUPON_NAME, validStart, validEnd), "首次发放必须真实生成两张正确的券");
        money("15.55", jdbc.queryForObject(
                "SELECT balance FROM customer_wallet WHERE customer_id=? AND wallet_type=2", BigDecimal.class, ACTIVE));
        assertEquals(1L, count(jdbc, "SELECT COUNT(*) FROM wallet_transaction WHERE customer_id=? "
                        + "AND wallet_type=2 AND transaction_type=4 AND amount=12.34 "
                        + "AND balance_before=3.21 AND balance_after=15.55 AND reference_type='VIP_MONTHLY'", ACTIVE),
                "专用金必须同时保存准确的前后余额流水");
        assertEquals(1L, count(jdbc, "SELECT COUNT(*) FROM vip_monthly_record WHERE customer_id=? "
                        + "AND `year_month`=? AND vip_level=? AND record_type=1", ACTIVE, month, LEVEL));
        assertEquals(1L, count(jdbc, "SELECT COUNT(*) FROM customer_benefit_log WHERE customer_id=? "
                        + "AND source_type=1 AND created_by=0 AND benefit_content LIKE ? "
                        + "AND benefit_content LIKE ?", ACTIVE, "%" + COUPON_NAME + "×2%", "%专用金¥12.34%"),
                "月记录之外必须留下包含券与专用金的真实权益日志");
        Map<String, List<Map<String, Object>>> granted = snapshot(jdbc);
        assertEquals(2, granted.get("customer_coupon").size());
        assertEquals(1, granted.get("wallet_transaction").size());
        assertEquals(1, granted.get("vip_monthly_record").size());
        assertEquals(1, granted.get("customer_benefit_log").size(), "成功发放不应混入失败日志");

        service.grantMonthlyBenefit(ACTIVE, month);
        service.grantMonthlyBenefit(ACTIVE, month);
        assertEquals(granted, snapshot(jdbc), "同月重放全部持久事实（含ID、时间、版本）必须完全不变");

        String nextMonth = YearMonth.from(today).plusMonths(1).toString();
        service.grantMonthlyBenefit(ACTIVE, nextMonth);
        money("27.89", jdbc.queryForObject(
                "SELECT balance FROM customer_wallet WHERE customer_id=? AND wallet_type=2", BigDecimal.class, ACTIVE));
        assertEquals(4L, count(jdbc, "SELECT COUNT(*) FROM customer_coupon WHERE customer_id=?", ACTIVE));
        assertEquals(2L, count(jdbc, "SELECT COUNT(*) FROM wallet_transaction WHERE customer_id=?", ACTIVE));
        assertEquals(2L, count(jdbc, "SELECT COUNT(*) FROM vip_monthly_record WHERE customer_id=?", ACTIVE));
        assertEquals(2L, count(jdbc, "SELECT COUNT(*) FROM customer_benefit_log WHERE customer_id=?", ACTIVE));
        Map<String, List<Map<String, Object>>> retried = snapshot(jdbc);
        service.grantMonthlyBenefit(ACTIVE, nextMonth);
        assertEquals(retried, snapshot(jdbc), "失败后的合法重试仍应保持同月幂等");

        Map<String, Object> activeBeforeDowngrade = jdbc.queryForMap("SELECT * FROM customer WHERE id=?", ACTIVE);
        Map<String, Object> normalBeforeDowngrade = jdbc.queryForMap("SELECT * FROM customer WHERE id=?", NORMAL);
        service.downgradeExpiredVips();
        assertEquals(1, jdbc.queryForObject("SELECT type FROM customer WHERE id=?", Integer.class, EXPIRED),
                "过期VIP必须实际降级");
        assertEquals(activeBeforeDowngrade, jdbc.queryForMap("SELECT * FROM customer WHERE id=?", ACTIVE),
                "有效VIP档案不能被降级任务修改");
        assertEquals(normalBeforeDowngrade, jdbc.queryForMap("SELECT * FROM customer WHERE id=?", NORMAL),
                "普通客户档案不能被降级任务修改");
        Map<String, List<Map<String, Object>>> downgraded = snapshot(jdbc);
        for (String table : List.of("customer_coupon", "customer_wallet", "wallet_transaction",
                "vip_monthly_record", "customer_benefit_log")) {
            assertEquals(retried.get(table), downgraded.get(table), "降级不得修改已有钱券权益事实：" + table);
        }
        service.downgradeExpiredVips();
        service.grantMonthlyBenefit(EXPIRED, month);
        assertEquals(downgraded, snapshot(jdbc), "降级任务重放及降级后领取不能新增权益或修改事实");
    }

    private static void customer(JdbcTemplate jdbc, long id, String name, int type,
                                 LocalDate start, LocalDate end) {
        jdbc.update("INSERT INTO customer "
                        + "(id,code,name,phone,type,vip_level,vip_start_date,vip_end_date,status,deleted) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,0)", id, "VIP_MYSQL_" + id, "隔离VIP测试_" + name,
                "test" + id, type, type == 2 ? LEVEL : null,
                start == null ? null : Date.valueOf(start), end == null ? null : Date.valueOf(end));
    }

    private static Map<String, List<Map<String, Object>>> snapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> facts = new LinkedHashMap<>();
        for (String table : List.of("customer", "customer_coupon", "customer_wallet", "wallet_transaction",
                "vip_monthly_record", "customer_benefit_log")) {
            String customerColumn = table.equals("customer") ? "id" : "customer_id";
            facts.put(table, jdbc.queryForList("SELECT * FROM " + table + " WHERE " + customerColumn
                    + " IN (?,?,?) ORDER BY id", ACTIVE, EXPIRED, NORMAL));
        }
        return facts;
    }

    private static long count(JdbcTemplate jdbc, String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        assertNotNull(result);
        return result;
    }

    private static void money(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "专用金余额必须精确一致");
    }
}
