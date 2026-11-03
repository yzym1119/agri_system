package com.agri_system.infrastructure.order.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/** 只保存POS请求身份与结果；JdbcTemplate使用项目既有同Flex事务连接。 */
@Repository
@RequiredArgsConstructor
public class PosSaleSubmissionRepository implements com.agri_system.domain.order.repository.PosSaleSubmissionRepository {
    private final JdbcTemplate jdbc;
    @Override
    public void reserve(String key,Long store,Long staff) {
        jdbc.update("INSERT INTO pos_sale_submission(request_key,store_id,staff_id,status) VALUES(?,?,?,'PENDING') ON DUPLICATE KEY UPDATE request_key=request_key",key,store,staff);
    }
    @Override
    public Optional<Submission> find(String key,boolean lock) {
        return jdbc.query("SELECT request_key,store_id,staff_id,request_hash,status,order_id FROM pos_sale_submission WHERE request_key=?"+(lock?" FOR UPDATE":""),
            (rs,n)->new Submission(rs.getString(1),rs.getLong(2),rs.getLong(3),rs.getString(4),rs.getString(5),rs.getObject(6,Long.class)),key).stream().findFirst();
    }
    @Override
    public void complete(String key,String hash,Long order) {
        if(jdbc.update("UPDATE pos_sale_submission SET status='COMPLETED',request_hash=?,order_id=?,updated_at=CURRENT_TIMESTAMP WHERE request_key=? AND status='PENDING'",hash,order,key)!=1)
            throw new IllegalStateException("收银请求状态已变化");
    }
    @Override
    public void cancel(String key) {
        if(jdbc.update("UPDATE pos_sale_submission SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE request_key=? AND status='PENDING'",key)!=1)
            throw new IllegalStateException("收银请求状态已变化");
    }
}
