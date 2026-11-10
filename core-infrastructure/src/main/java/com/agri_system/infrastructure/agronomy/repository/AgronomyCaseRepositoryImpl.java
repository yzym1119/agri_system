package com.agri_system.infrastructure.agronomy.repository;

import com.agri_system.domain.agronomy.model.AgronomyCase;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import com.agri_system.domain.agronomy.model.AgronomyCaseEntry;
import com.agri_system.domain.agronomy.repository.AgronomyCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AgronomyCaseRepositoryImpl implements AgronomyCaseRepository {
    private final JdbcTemplate jdbc;
    private static final String CASE_COLUMNS = "id,customer_id,store_id,request_key,request_hash,question,region,crop,growth_stage,status,version,facts_version,assigned_staff_id,created_at,updated_at";
    private static final String ENTRY_COLUMNS = "id,case_id,actor_type,actor_id,request_key,request_hash,type,body,source_text,region,crop,growth_stage,assigned_staff_id,expected_version,result_version,result_facts_version,result_status,created_at";

    @Override public Optional<AgronomyCase> find(Long id, boolean lock) {
        return one("id=?", lock, id);
    }
    @Override public Optional<AgronomyCase> findOwned(Long id, Long customer, boolean lock) {
        return one("id=? AND customer_id=?", lock, id, customer);
    }
    @Override public Optional<AgronomyCase> findCreation(Long customer, String key) {
        // 创建请求和首条记录不可变，重放不升级重复插入取得的共享索引锁。
        return one("customer_id=? AND request_key=?", false, customer, key);
    }
    private Optional<AgronomyCase> one(String where, boolean lock, Object... args) {
        return jdbc.query("SELECT " + CASE_COLUMNS + " FROM agronomy_case WHERE " + where
                + (lock ? " FOR UPDATE" : ""), AgronomyCaseRepositoryImpl::mapCase, args).stream().findFirst();
    }
    @Override public boolean insertCase(AgronomyCase c) {
        return jdbc.update("INSERT IGNORE INTO agronomy_case(customer_id,store_id,request_key,request_hash,question,region,crop,growth_stage,status,version,facts_version,assigned_staff_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                c.customerId(), c.storeId(), c.requestKey(), c.requestHash(), c.question(), c.facts().region(),
                c.facts().crop(), c.facts().growthStage(), c.status(), c.version(), c.factsVersion(),
                c.assignedStaffId(), c.createdAt(), c.updatedAt()) == 1;
    }
    @Override public Optional<AgronomyCaseEntry> findRequest(String actor, Long actorId, String key) {
        return jdbc.query("SELECT " + ENTRY_COLUMNS + " FROM agronomy_case_entry WHERE actor_type=? AND actor_id=? AND request_key=?",
                AgronomyCaseRepositoryImpl::mapEntry, actor, actorId, key).stream().findFirst();
    }
    @Override public boolean insertEntry(AgronomyCaseEntry e) {
        return jdbc.update("INSERT IGNORE INTO agronomy_case_entry(case_id,actor_type,actor_id,request_key,request_hash,type,body,source_text,region,crop,growth_stage,assigned_staff_id,expected_version,result_version,result_facts_version,result_status,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                e.caseId(), e.actorType(), e.actorId(), e.requestKey(), e.requestHash(), e.type(), e.body(),
                e.sourceText(), e.facts().region(), e.facts().crop(), e.facts().growthStage(), e.assignedStaffId(),
                e.expectedVersion(), e.resultVersion(), e.resultFactsVersion(), e.resultStatus(), e.createdAt()) == 1;
    }
    @Override public boolean update(Long id, long expected, Facts f, String state, long factsVersion, Long assigned, LocalDateTime at) {
        return jdbc.update("UPDATE agronomy_case SET region=?,crop=?,growth_stage=?,status=?,version=version+1,facts_version=?,assigned_staff_id=?,updated_at=? WHERE id=? AND version=? AND version>0 AND version<9223372036854775807",
                f.region(), f.crop(), f.growthStage(), state, factsVersion, assigned, at, id, expected) == 1;
    }
    @Override public List<AgronomyCase> page(Long customer, Long store, Long assigned, String status, Long before) {
        var args = new ArrayList<Object>();
        String where = " WHERE 1=1";
        if (customer != null) { where += " AND customer_id=?"; args.add(customer); }
        if (store != null) { where += " AND store_id=?"; args.add(store); }
        if (assigned != null) { where += " AND assigned_staff_id=?"; args.add(assigned); }
        if (status != null) { where += " AND status=?"; args.add(status); }
        if (before != null) { where += " AND id<?"; args.add(before); }
        return jdbc.query("SELECT " + CASE_COLUMNS + " FROM agronomy_case" + where + " ORDER BY id DESC LIMIT 21",
                AgronomyCaseRepositoryImpl::mapCase, args.toArray());
    }
    @Override public List<AgronomyCaseEntry> entries(Long id, boolean customer, long version, Long before) {
        var args = new ArrayList<Object>(); args.add(id); args.add(version);
        String filter = customer ? " AND ((actor_type='CUSTOMER' AND type IN ('QUESTION','SUPPLEMENT','HUMAN_REQUEST')) OR (actor_type='STAFF' AND type='HUMAN_REPLY'))" : "";
        if (before != null) { filter += " AND id<?"; args.add(before); }
        // 只读该主档快照对应的追加，避免两次读取间新回复与旧version混装。
        return jdbc.query("SELECT " + ENTRY_COLUMNS + " FROM agronomy_case_entry WHERE case_id=? AND result_version<=?"
                + filter + " ORDER BY id DESC LIMIT 21", AgronomyCaseRepositoryImpl::mapEntry, args.toArray());
    }
    @Override public Optional<AgronomyCaseEntry> latestHumanReply(Long id, long version) {
        return jdbc.query("SELECT " + ENTRY_COLUMNS + " FROM agronomy_case_entry WHERE case_id=? AND type='HUMAN_REPLY' AND actor_type='STAFF' AND result_version<=? ORDER BY id DESC LIMIT 1",
                AgronomyCaseRepositoryImpl::mapEntry, id, version).stream().findFirst();
    }
    private static Facts facts(ResultSet r) throws SQLException {
        return new Facts(r.getString("region"), r.getString("crop"), r.getString("growth_stage"));
    }
    private static AgronomyCase mapCase(ResultSet r, int n) throws SQLException {
        return new AgronomyCase(r.getLong("id"), r.getLong("customer_id"), r.getLong("store_id"),
                r.getString("request_key"), r.getString("request_hash"), r.getString("question"), facts(r),
                r.getString("status"), r.getLong("version"), r.getLong("facts_version"),
                r.getObject("assigned_staff_id", Long.class), r.getObject("created_at", LocalDateTime.class),
                r.getObject("updated_at", LocalDateTime.class));
    }
    private static AgronomyCaseEntry mapEntry(ResultSet r, int n) throws SQLException {
        return new AgronomyCaseEntry(r.getLong("id"), r.getLong("case_id"), r.getString("actor_type"),
                r.getLong("actor_id"), r.getString("request_key"), r.getString("request_hash"),
                r.getString("type"), r.getString("body"), r.getString("source_text"), facts(r),
                r.getObject("assigned_staff_id", Long.class), r.getLong("expected_version"), r.getLong("result_version"),
                r.getLong("result_facts_version"), r.getString("result_status"), r.getObject("created_at", LocalDateTime.class));
    }
}
