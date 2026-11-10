package com.agri_system.infrastructure.auth.repository;

import com.agri_system.domain.auth.model.CustomerIdentityEnrollment;
import com.agri_system.domain.auth.repository.CustomerIdentityEnrollmentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** 同Flex事务手动绑定敏感申请，避免通用SQL参数/结果日志。 */
@Repository
@RequiredArgsConstructor
public class CustomerIdentityEnrollmentRepositoryImpl implements CustomerIdentityEnrollmentRepository {
    private final JdbcTemplate jdbc;
    private static final String FIELDS="id,request_key,fingerprint,active_subject,app_id,open_id,phone,display_name,verified_at,status,review_reason,customer_id,identity_id,identity_auth_version,decision_request_key,decision_fingerprint,reviewed_by,verification_method,decision_reason,reviewed_at,created_at,updated_at";
    @Override public CustomerIdentityEnrollment acquire(CustomerIdentityEnrollment row){
        transaction();
        // 明确原UUID优先。旧申请拒绝后出现新锚，重放仍只返回旧申请，不能混查两行。
        var original=findByRequestKey(row.getRequestKey(),true);if(original.isPresent())return original.get();
        update("INSERT INTO customer_identity_enrollment(request_key,fingerprint,active_subject,app_id,open_id,phone,display_name,verified_at,status) VALUES(?,?,?,?,?,?,?,?,'PROCESSING') ON DUPLICATE KEY UPDATE id=id",
                row.getRequestKey(),row.getFingerprint(),row.getActiveSubject(),row.getAppId(),row.getOpenId(),row.getPhone(),row.getDisplayName(),row.getVerifiedAt());
        // upsert按主键→唯一索引取锁；OR二级索引FOR UPDATE会反过来造成死锁。
        // 先只定位，再固定主键锁。upsert已持有冲突行，定位期间不会被拒绝/改写。
        // 初查之后也可能出现并已拒绝的原UUID；再次优先原请求，不能被后来的锚覆盖。
        original=findByRequestKey(row.getRequestKey(),true);if(original.isPresent())return original.get();
        var rows=query("WHERE active_subject=?",row.getActiveSubject());
        if(rows.size()!=1)throw new IllegalArgumentException("请求号与当前微信申请冲突，请核对原申请");
        return findById(rows.get(0).getId(),true).orElseThrow(()->new IllegalStateException("原申请读取失败"));
    }
    @Override public Optional<CustomerIdentityEnrollment> findByRequestKey(String key,boolean lock){
        if(lock)transaction();var row=query("WHERE request_key=?",key).stream().findFirst();return lock&&row.isPresent()?findById(row.get().getId(),true):row;
    }
    @Override public Optional<CustomerIdentityEnrollment> findById(Long id,boolean lock){if(lock)transaction();return query("WHERE id=?"+(lock?" FOR UPDATE":""),id).stream().findFirst();}
    @Override public List<CustomerIdentityEnrollment> page(String status,Long before,int limit){
        if(limit<1||limit>100)throw new IllegalArgumentException("申请查询数量无效");
        return before==null?query("WHERE status=? ORDER BY id DESC LIMIT ?",status,limit):query("WHERE status=? AND id<? ORDER BY id DESC LIMIT ?",status,before,limit);
    }
    @Override public void finishRegistration(CustomerIdentityEnrollment row){
        transaction();if(!List.of("CREATED","REVIEW_REQUIRED").contains(row.getStatus()))throw new IllegalArgumentException("申请结果状态无效");
        changed(update("UPDATE customer_identity_enrollment SET status=?,review_reason=?,customer_id=?,identity_id=?,identity_auth_version=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PROCESSING'",
                row.getStatus(),row.getReviewReason(),row.getCustomerId(),row.getIdentityId(),row.getIdentityAuthVersion(),row.getId()));
    }
    @Override public void claimDecision(CustomerIdentityEnrollment row){
        transaction();changed(update("UPDATE customer_identity_enrollment SET decision_request_key=?,decision_fingerprint=?,reviewed_by=?,verification_method=?,decision_reason=? WHERE id=? AND status='REVIEW_REQUIRED' AND decision_request_key IS NULL",
                row.getDecisionRequestKey(),row.getDecisionFingerprint(),row.getReviewedBy(),row.getVerificationMethod(),row.getDecisionReason(),row.getId()));
    }
    @Override public void finishDecision(CustomerIdentityEnrollment row){
        transaction();if(!List.of("LINKED","REJECTED").contains(row.getStatus()))throw new IllegalArgumentException("核验结果状态无效");
        changed(update("UPDATE customer_identity_enrollment SET status=?,customer_id=?,identity_id=?,identity_auth_version=?,active_subject=?,reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='REVIEW_REQUIRED' AND decision_request_key=? AND decision_fingerprint=?",
                row.getStatus(),row.getCustomerId(),row.getIdentityId(),row.getIdentityAuthVersion(),"REJECTED".equals(row.getStatus())?null:row.getActiveSubject(),row.getId(),row.getDecisionRequestKey(),row.getDecisionFingerprint()));
    }
    private int update(String sql,Object... values){return jdbc.execute((ConnectionCallback<Integer>) c->{try(var statement=c.prepareStatement(sql)){bind(statement,values);return statement.executeUpdate();}});}
    private List<CustomerIdentityEnrollment> query(String tail,Object... values){
        return jdbc.execute((ConnectionCallback<List<CustomerIdentityEnrollment>>) c->{try(var statement=c.prepareStatement("SELECT "+FIELDS+" FROM customer_identity_enrollment "+tail)){
            bind(statement,values);try(var rows=statement.executeQuery()){var result=new java.util.ArrayList<CustomerIdentityEnrollment>();while(rows.next())result.add(map(rows));return List.copyOf(result);}
        }});
    }
    private static void bind(java.sql.PreparedStatement statement,Object[] values)throws SQLException{
        for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i] instanceof java.time.LocalDateTime time?java.sql.Timestamp.valueOf(time):values[i]);
    }
    private static void transaction(){if(com.mybatisflex.core.transaction.TransactionContext.getXID()==null)throw new IllegalStateException("身份申请写入必须在原Flex事务内");}
    private static void changed(int rows){if(rows!=1)throw new IllegalStateException("申请状态已改变，请查询原结果");}
    private static Long number(ResultSet rows,String name)throws SQLException{long value=rows.getLong(name);return rows.wasNull()?null:value;}
    private static java.time.LocalDateTime time(ResultSet rows,String name)throws SQLException{var value=rows.getTimestamp(name);return value==null?null:value.toLocalDateTime();}
    private static CustomerIdentityEnrollment map(ResultSet r)throws SQLException{
        var row=new CustomerIdentityEnrollment();row.setId(number(r,"id"));row.setRequestKey(r.getString("request_key"));row.setFingerprint(r.getString("fingerprint"));row.setActiveSubject(r.getString("active_subject"));
        row.setAppId(r.getString("app_id"));row.setOpenId(r.getString("open_id"));row.setPhone(r.getString("phone"));row.setDisplayName(r.getString("display_name"));row.setVerifiedAt(time(r,"verified_at"));row.setStatus(r.getString("status"));row.setReviewReason(r.getString("review_reason"));
        row.setCustomerId(number(r,"customer_id"));row.setIdentityId(number(r,"identity_id"));row.setIdentityAuthVersion(number(r,"identity_auth_version"));row.setDecisionRequestKey(r.getString("decision_request_key"));row.setDecisionFingerprint(r.getString("decision_fingerprint"));row.setReviewedBy(number(r,"reviewed_by"));
        row.setVerificationMethod(r.getString("verification_method"));row.setDecisionReason(r.getString("decision_reason"));row.setReviewedAt(time(r,"reviewed_at"));row.setCreatedAt(time(r,"created_at"));row.setUpdatedAt(time(r,"updated_at"));return row;
    }
}
