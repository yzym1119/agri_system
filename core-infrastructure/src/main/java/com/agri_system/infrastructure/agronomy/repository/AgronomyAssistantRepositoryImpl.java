package com.agri_system.infrastructure.agronomy.repository;

import com.agri_system.domain.agronomy.assistant.AgronomyAssistantRepository;
import com.agri_system.domain.agronomy.assistant.AssistantRequestMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AgronomyAssistantRepositoryImpl implements AgronomyAssistantRepository {
    private final JdbcTemplate jdbc;
    @Override public Optional<AssistantRequestMetadata> find(Long id) {
        return jdbc.query("SELECT * FROM agronomy_case_entry WHERE id=? AND type='ASSISTANT_REQUEST' AND assistant_state IS NOT NULL",(r,n)->
                new AssistantRequestMetadata(r.getLong("id"),r.getString("assistant_state"),r.getString("assistant_snapshot"),r.getString("assistant_outcome"),
                        r.getObject("assistant_budget_day",LocalDate.class),r.getLong("assistant_model_reserved_microusd"),r.getLong("assistant_search_reserved_microusd"),r.getString("assistant_claim_key"),r.getObject("assistant_updated_at",LocalDateTime.class),r.getObject("assistant_closed_at",LocalDateTime.class)),id).stream().findFirst();
    }
    @Override public void initialize(AssistantRequestMetadata m) {
        if (jdbc.update("UPDATE agronomy_case_entry SET assistant_state='QUEUED',assistant_snapshot=?,assistant_budget_day=?,assistant_model_reserved_microusd=?,assistant_search_reserved_microusd=?,assistant_updated_at=? WHERE id=? AND type='ASSISTANT_REQUEST' AND actor_type='CUSTOMER' AND assistant_state IS NULL",
                m.snapshot(),m.budgetDay(),m.modelReservedMicrousd(),m.searchReservedMicrousd(),m.updatedAt(),m.entryId())!=1) throw new IllegalStateException("AI原请求元数据未能准备");
    }
    @Override public boolean hasOpen(Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM agronomy_case_entry WHERE case_id=? AND type='ASSISTANT_REQUEST' AND assistant_state IN ('QUEUED','RUNNING','UNKNOWN') AND assistant_closed_at IS NULL",Boolean.class,id));
    }
    @Override public boolean closeWaiting(Long id,LocalDateTime at) {
        return jdbc.update("UPDATE agronomy_case_entry SET assistant_closed_at=? WHERE id=? AND type='ASSISTANT_REQUEST' AND actor_type='CUSTOMER' AND assistant_state IN ('RUNNING','UNKNOWN') AND assistant_claim_key IS NOT NULL AND assistant_closed_at IS NULL",at,id)==1;
    }
    @Override public Usage usage(Long store,LocalDate day) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN e.assistant_model_reserved_microusd>0 THEN 1 ELSE 0 END),0) AS models,COALESCE(SUM(CASE WHEN e.assistant_search_reserved_microusd>0 THEN 1 ELSE 0 END),0) AS searches,COALESCE(SUM(CAST(e.assistant_model_reserved_microusd AS DECIMAL(65,0))+CAST(e.assistant_search_reserved_microusd AS DECIMAL(65,0))),0) AS cost FROM agronomy_case_entry e JOIN agronomy_case c ON c.id=e.case_id WHERE c.store_id=? AND e.type='ASSISTANT_REQUEST' AND e.assistant_budget_day=?",
                (r,n)->new Usage(exact(r.getBigDecimal("models")),exact(r.getBigDecimal("searches")),exact(r.getBigDecimal("cost"))),store,day);
    }
    private static long exact(BigDecimal v) { try {if(v==null||v.signum()<0)throw new ArithmeticException();return v.longValueExact();}catch(ArithmeticException bad){throw new IllegalStateException("AI预占合计待核对");} }
    @Override public boolean claim(Long id,String claim,LocalDateTime at) {
        return jdbc.update("UPDATE agronomy_case_entry SET assistant_state='RUNNING',assistant_claim_key=?,assistant_updated_at=? WHERE id=? AND assistant_state='QUEUED' AND assistant_claim_key IS NULL AND assistant_closed_at IS NULL",claim,at,id)==1;
    }
    @Override public boolean failBeforeDispatch(Long id,String outcome,LocalDateTime at) {
        return jdbc.update("UPDATE agronomy_case_entry SET assistant_state='FAILED',assistant_outcome=?,assistant_model_reserved_microusd=0,assistant_search_reserved_microusd=0,assistant_updated_at=? WHERE id=? AND assistant_state='QUEUED' AND assistant_claim_key IS NULL",outcome,at,id)==1;
    }
    @Override public boolean finish(Long id,String claim,String state,String outcome,LocalDateTime at) {
        if(!java.util.Set.of("COMPLETED","UNKNOWN").contains(state))throw new IllegalArgumentException("AI处理状态无效");
        return jdbc.update("UPDATE agronomy_case_entry SET assistant_state=?,assistant_outcome=?,assistant_updated_at=? WHERE id=? AND assistant_state IN ('RUNNING','UNKNOWN') AND assistant_claim_key=?",state,outcome,at,id,claim)==1;
    }
    @Override public Optional<String> steps(Long id) {
        return jdbc.query("SELECT assistant_steps FROM agronomy_case_entry WHERE id=? AND type='ASSISTANT_REQUEST'",(r,n)->r.getString(1),id).stream().filter(java.util.Objects::nonNull).findFirst();
    }
    @Override public boolean compareSteps(Long id,String claim,String expected,String next,LocalDateTime at) {
        return jdbc.update("UPDATE agronomy_case_entry SET assistant_steps=?,assistant_updated_at=? WHERE id=? AND type='ASSISTANT_REQUEST' AND assistant_state IN ('RUNNING','UNKNOWN') AND assistant_claim_key=? AND (assistant_steps=? OR (assistant_steps IS NULL AND ? IS NULL))",
                next,at,id,claim,expected,expected)==1;
    }
}
