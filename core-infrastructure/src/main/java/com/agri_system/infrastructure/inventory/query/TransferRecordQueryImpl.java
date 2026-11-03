package com.agri_system.infrastructure.inventory.query;

import com.agri_system.domain.inventory.query.TransferRecordQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TransferRecordQueryImpl implements TransferRecordQuery {
    private final JdbcTemplate jdbcTemplate;
    @Override
    public Result find(Criteria query) {
        StringBuilder fromSql = new StringBuilder("""
                FROM inventory_transfer o
                LEFT JOIN product p ON o.product_id = p.id
                LEFT JOIN store fs ON o.from_store_id = fs.id
                LEFT JOIN store ts ON o.to_store_id = ts.id
                LEFT JOIN staff st ON o.created_by = st.id
                LEFT JOIN (
                    SELECT transfer_id, COUNT(1) AS batch_count
                    FROM inventory_transfer_detail
                    GROUP BY transfer_id
                ) td ON td.transfer_id = o.id
                WHERE 1 = 1
                """);

        List<Object> params = new ArrayList<>();

        // JDBC不会自动应用Flex租户条件；调出或调入任一端属于当前范围才可见。
        Object[] tenantStores = com.mybatisflex.core.tenant.TenantManager.getTenantIds();
        if (tenantStores != null) {
            if (tenantStores.length == 0) {
                fromSql.append(" AND 1 = 0 ");
            } else {
                String placeholders = String.join(",", java.util.Collections.nCopies(tenantStores.length, "?"));
                fromSql.append(" AND (o.from_store_id IN (").append(placeholders)
                        .append(") OR o.to_store_id IN (").append(placeholders).append(")) ");
                params.addAll(java.util.Arrays.asList(tenantStores));
                params.addAll(java.util.Arrays.asList(tenantStores));
            }
        }

        if (query.fromStoreId() != null) {
            fromSql.append(" AND o.from_store_id = ? ");
            params.add(query.fromStoreId());
        }

        if (query.toStoreId() != null) {
            fromSql.append(" AND o.to_store_id = ? ");
            params.add(query.toStoreId());
        }

        if (StringUtils.hasText(query.productName())) {
            fromSql.append(" AND p.name LIKE ? ");
            params.add("%" + query.productName().trim() + "%");
        }

        if (StringUtils.hasText(query.startTime())) {
            fromSql.append(" AND o.created_at >= ? ");
            params.add(LocalDateTime.parse(query.startTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        if (StringUtils.hasText(query.endTime())) {
            fromSql.append(" AND o.created_at <= ? ");
            params.add(LocalDateTime.parse(query.endTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        String countSql = "SELECT COUNT(1) " + fromSql;

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long totalRows = total == null ? 0L : total;

        String dataSql = """
                SELECT
                    o.id AS transfer_id,
                    o.transfer_no AS transfer_no,
                    o.from_store_id AS from_store_id,
                    fs.name AS from_store_name,
                    o.to_store_id AS to_store_id,
                    ts.name AS to_store_name,
                    o.product_id AS product_id,
                    p.name AS product_name,
                    p.specification AS product_spec,
                    o.quantity AS quantity,
                    COALESCE(td.batch_count, 0) AS batch_count,
                    o.created_by AS created_by,
                    st.name AS created_by_name,
                    o.created_at AS created_at,
                    o.remark AS remark
                """ + fromSql + """
                ORDER BY o.created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(query.pageSize());
        dataParams.add((long) (query.pageNum() - 1) * query.pageSize());

        List<Row> records = jdbcTemplate.query(dataSql, dataParams.toArray(), (rs, rowNum) -> {
            Number batchCount = (Number) rs.getObject("batch_count");
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new Row(rs.getLong("transfer_id"),
                    rs.getString("transfer_no"),
                    (Long) rs.getObject("from_store_id"),
                    rs.getString("from_store_name"),
                    (Long) rs.getObject("to_store_id"),
                    rs.getString("to_store_name"),
                    rs.getLong("product_id"),
                    rs.getString("product_name"),
                    rs.getString("product_spec"),
                    rs.getBigDecimal("quantity"),
                    batchCount == null ? 0 : batchCount.intValue(),
                    (Long) rs.getObject("created_by"),
                    rs.getString("created_by_name"),
                    createdAt != null ? createdAt.toLocalDateTime() : null,
                    rs.getString("remark"));
        });

        return new Result(totalRows,records);
    }
}
