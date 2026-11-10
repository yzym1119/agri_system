package com.agri_system.infrastructure.agronomy.repository;

import com.agri_system.domain.agronomy.model.KnowledgeRevision;
import com.agri_system.domain.agronomy.model.KnowledgeRevision.Content;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class KnowledgeRevisionRepositoryImpl implements KnowledgeRevisionRepository {
    private final JdbcTemplate jdbc;
    private static final String COLUMNS="k.id,k.logical_key,k.revision,k.store_id,k.title,k.summary,k.crop,k.region,k.growth_stage,k.applies_to,k.exclusions,k.body,k.source_text,k.audience,k.risk_level,k.effective_from,k.effective_until,k.content_hash,k.state,k.version,k.created_by,k.created_at,k.reviewed_by,k.reviewed_at,k.published_at,k.withdrawn_at,k.request_key,k.request_hash";
    private static final String TABLE=" FROM agronomy_knowledge_revision k";
    private static final String PUBLIC=TABLE+" JOIN store s ON s.id=k.store_id WHERE k.state='PUBLISHED' AND k.audience='PUBLIC' AND k.risk_level='LOW' AND k.reviewed_by>0 AND k.reviewed_at IS NOT NULL AND s.status=1 AND s.deleted=0 AND k.effective_from<=? AND k.effective_until>=?";
    private static final String SCOPE=" AND k.region=? AND k.crop=? AND k.growth_stage=?";
    @Override public Optional<KnowledgeRevision> find(String key,int revision,boolean lock){return jdbc.query("SELECT "+COLUMNS+TABLE+" WHERE k.logical_key=? AND k.revision=?"+(lock?" FOR UPDATE":""),KnowledgeRevisionRepositoryImpl::map,key,revision).stream().findFirst();}
    @Override public Optional<KnowledgeRevision> findSubmission(Long actor,String request,boolean lock){return jdbc.query("SELECT "+COLUMNS+TABLE+" WHERE k.created_by=? AND k.request_key=?"+(lock?" FOR UPDATE":""),KnowledgeRevisionRepositoryImpl::map,actor,request).stream().findFirst();}
    @Override public int latestRevision(String key){Integer latest=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0) FROM agronomy_knowledge_revision WHERE logical_key=?",Integer.class,key);return latest==null?0:latest;}
    @Override public int latestPublishedRevision(String key){Integer latest=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0) FROM agronomy_knowledge_revision WHERE logical_key=? AND published_at IS NOT NULL",Integer.class,key);return latest==null?0:latest;}
    @Override public boolean insertIfAbsent(KnowledgeRevision r){var c=r.content();return jdbc.update("INSERT IGNORE INTO agronomy_knowledge_revision(logical_key,revision,store_id,title,summary,crop,region,growth_stage,applies_to,exclusions,body,source_text,audience,risk_level,effective_from,effective_until,content_hash,state,version,created_by,created_at,request_key,request_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",r.logicalKey(),r.revision(),r.storeId(),c.title(),c.summary(),c.crop(),c.region(),c.growthStage(),c.appliesTo(),c.exclusions(),c.body(),c.sourceText(),c.audience(),c.riskLevel(),c.effectiveFrom(),c.effectiveUntil(),r.contentHash(),r.state(),r.version(),r.createdBy(),r.createdAt(),r.requestKey(),r.requestHash())==1;}
    @Override public List<KnowledgeRevision> published(String key){return jdbc.query("SELECT "+COLUMNS+TABLE+" WHERE k.logical_key=? AND k.state='PUBLISHED' ORDER BY k.revision FOR UPDATE",KnowledgeRevisionRepositoryImpl::map,key);}
    @Override public boolean changeState(Long id,long expected,String state,Long actor,LocalDateTime now){String extra=switch(state){case "REVIEWED"->", reviewed_by=?, reviewed_at=?";case "PUBLISHED"->", published_at=?";case "WITHDRAWN"->", withdrawn_at=?";default->throw new IllegalArgumentException("知识状态无效");};var args=new ArrayList<Object>();args.add(state);if(state.equals("REVIEWED"))args.add(actor);args.add(now);args.add(id);args.add(expected);return jdbc.update("UPDATE agronomy_knowledge_revision SET state=?,version=version+1"+extra+" WHERE id=? AND version=? AND version>0 AND version<9223372036854775807",args.toArray())==1;}
    @Override public List<KnowledgeRevision> page(Long store,Long before){var args=new ArrayList<Object>();String where=" WHERE 1=1";if(store!=null){where+=" AND k.store_id=?";args.add(store);}if(before!=null){where+=" AND k.id<?";args.add(before);}return jdbc.query("SELECT "+COLUMNS+TABLE+where+" ORDER BY k.id DESC LIMIT 21",KnowledgeRevisionRepositoryImpl::map,args.toArray());}
    @Override public List<Scope> publicScopes(LocalDate today){return jdbc.query("SELECT DISTINCT k.region,k.crop,k.growth_stage"+PUBLIC+" ORDER BY k.region,k.crop,k.growth_stage LIMIT 201",(r,n)->new Scope(r.getString("region"),r.getString("crop"),r.getString("growth_stage")),today,today);}
    @Override public List<KnowledgeRevision> publicPage(Scope scope,LocalDate today,Long after,String keyword){var args=new ArrayList<Object>(List.of(today,today,scope.region(),scope.crop(),scope.growthStage()));String filter="";if(after!=null){filter+=" AND k.id>?";args.add(after);}if(keyword!=null&&!keyword.isBlank()){filter+=" AND (k.title LIKE ? ESCAPE '!' OR k.summary LIKE ? ESCAPE '!')";String q="%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(q);args.add(q);}return jdbc.query("SELECT "+COLUMNS+PUBLIC+SCOPE+filter+" ORDER BY k.id LIMIT 21",KnowledgeRevisionRepositoryImpl::map,args.toArray());}
    @Override public Optional<KnowledgeRevision> publicCurrent(String key,Scope scope,LocalDate today){return jdbc.query("SELECT "+COLUMNS+PUBLIC+SCOPE+" AND k.logical_key=? ORDER BY k.revision DESC LIMIT 2",KnowledgeRevisionRepositoryImpl::map,today,today,scope.region(),scope.crop(),scope.growthStage(),key).stream().reduce((a,b)->{throw new IllegalStateException("知识发布版本待核对");});}
    private static KnowledgeRevision map(ResultSet r,int n)throws SQLException{return new KnowledgeRevision(r.getLong("id"),r.getString("logical_key"),r.getInt("revision"),r.getLong("store_id"),new Content(r.getString("title"),r.getString("summary"),r.getString("crop"),r.getString("region"),r.getString("growth_stage"),r.getString("applies_to"),r.getString("exclusions"),r.getString("body"),r.getString("source_text"),r.getString("audience"),r.getString("risk_level"),r.getObject("effective_from",LocalDate.class),r.getObject("effective_until",LocalDate.class)),r.getString("content_hash"),r.getString("state"),r.getLong("version"),r.getLong("created_by"),r.getObject("created_at",LocalDateTime.class),r.getObject("reviewed_by",Long.class),r.getObject("reviewed_at",LocalDateTime.class),r.getObject("published_at",LocalDateTime.class),r.getObject("withdrawn_at",LocalDateTime.class),r.getString("request_key"),r.getString("request_hash"));}
}
