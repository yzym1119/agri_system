package com.agri_system.infrastructure.performance.repository;

import com.agri_system.domain.performance.model.CommissionRule;
import com.agri_system.infrastructure.performance.mapper.CommissionRuleMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 在内存数据库执行仓储生成的真实筛选 SQL，防止商品与套餐的数值 ID 交叉命中。 */
class CommissionRuleRepositoryMatchingTest {
    private CommissionRuleRepositoryImpl repository;
    private JdbcDataSource source;

    @BeforeEach
    void setup() throws Exception {
        source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:commission_" + java.util.UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE commission_rule(id BIGINT PRIMARY KEY,rule_category INT,target_id BIGINT,staff_id BIGINT,enabled INT,priority INT)");
        }
        CommissionRuleMapper mapper = mock(CommissionRuleMapper.class);
        when(mapper.selectListByQuery(any(QueryWrapper.class))).thenAnswer(call -> query(call.getArgument(0)));
        repository = new CommissionRuleRepositoryImpl(mapper);
    }

    @Test
    void packageTargetCannotMatchProductId() throws Exception {
        insert("1,5,21,NULL,1,100");
        insert("2,5,8,NULL,1,1");
        assertEquals(2L, repository.findBestSalesRule(21L, 8L, 9L).orElseThrow().getId());
    }

    @Test
    void salesTargetCannotMatchPackageId() throws Exception {
        insert("1,1,8,NULL,1,100");
        insert("2,1,21,NULL,1,1");
        assertEquals(2L, repository.findBestSalesRule(21L, 8L, 9L).orElseThrow().getId());
    }

    @Test
    void packageOnlyLookupCannotFallBackToGlobalProductRule() throws Exception {
        insert("1,1,NULL,NULL,1,100");
        assertTrue(repository.findBestSalesRule(null, 8L, 9L).isEmpty());
    }

    @Test
    void packageRuleStillWinsAndOtherStaffCannotMatch() throws Exception {
        insert("1,1,21,9,1,100"); insert("2,5,8,7,1,100"); insert("3,5,8,NULL,1,1");
        assertEquals(3L, repository.findBestSalesRule(21L, 8L, 9L).orElseThrow().getId());
    }

    private void insert(String values) throws Exception {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO commission_rule VALUES(" + values + ")");
        }
    }

    private List<CommissionRule> query(QueryWrapper wrapper) throws Exception {
        List<CommissionRule> result = new ArrayList<>();
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(wrapper.from("commission_rule").toSQL())) {
            while (rs.next()) {
                CommissionRule rule = new CommissionRule(); rule.setId(rs.getLong("id"));
                rule.setRuleCategory(rs.getInt("rule_category")); rule.setTargetId((Long)rs.getObject("target_id"));
                rule.setStaffId((Long)rs.getObject("staff_id")); rule.setPriority(rs.getInt("priority")); result.add(rule);
            }
        }
        return result;
    }
}
