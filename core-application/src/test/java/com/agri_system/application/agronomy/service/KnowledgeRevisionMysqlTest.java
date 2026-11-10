package com.agri_system.application.agronomy.service;

import com.agri_system.application.config.JdbcAccessConfig;
import com.agri_system.domain.agronomy.model.KnowledgeRevision.Content;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository.Scope;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import com.agri_system.infrastructure.agronomy.repository.KnowledgeRevisionRepositoryImpl;
import com.agri_system.infrastructure.store.repository.StorePublicationRepositoryImpl;
import com.agri_system.infrastructure.system.log.mapper.SysOperationLogMapper;
import com.agri_system.infrastructure.system.log.repository.SysOperationLogRepositoryImpl;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.core.mybatis.*;
import com.mybatisflex.core.tenant.*;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="mysql.it",matches="true")
class KnowledgeRevisionMysqlTest {
    JdbcTemplate server,jdbc;String schema;boolean created;TenantFactory previous;TransactionTemplate tx;KnowledgeRevisionService service;KnowledgeRevisionRepositoryImpl rows;StaffRepository staff;SysRoleRepository roles;StorePublicationRepositoryImpl stores;SysOperationLogRepository logs;
    @BeforeEach void setup()throws Exception{
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        String base="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/",options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
        server=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));schema="agri_knowledge_it_"+UUID.randomUUID().toString().replace("-","");server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");created=true;
        var source=new DriverManagerDataSource(base+schema+options,user,password);var init=new JdbcTemplate(source);init.execute("CREATE TABLE staff(id BIGINT PRIMARY KEY)");
        try(var c=source.getConnection()){for(String file:List.of("V1__store.sql","V46__staff_rbac_and_log.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/"+file));}
        init.execute("ALTER TABLE store ADD deleted INT NOT NULL DEFAULT 0");init.update("INSERT INTO store(id,code,name) VALUES(1,'TEST','虚构知识门店'),(2,'OTHER','其他门店')");
        try(var c=source.getConnection()){for(String file:List.of("V147__client_store_publication.sql","V148__agronomy_knowledge_revision.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/"+file));}
        var config=new FlexConfiguration(new Environment("knowledge",new SpringManagedTransactionFactory(),new FlexDataSource("knowledge",source,false)));config.setMapUnderscoreToCamelCase(true);var factory=new FlexSqlSessionFactoryBuilder().build(config);config.addMapper(SysOperationLogMapper.class);config.addMapper(Probe.class);var template=new SqlSessionTemplate(factory);jdbc=new JdbcTemplate(config.getEnvironment().getDataSource());tx=new TransactionTemplate(new JdbcAccessConfig().transactionManager(factory));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        rows=new KnowledgeRevisionRepositoryImpl(jdbc);stores=new StorePublicationRepositoryImpl(jdbc);logs=new SysOperationLogRepositoryImpl(template.getMapper(SysOperationLogMapper.class));staff=mock(StaffRepository.class);roles=mock(SysRoleRepository.class);var actor=new Staff();actor.setId(9L);actor.setStoreId(1L);actor.setStatus(1);actor.setDeleted(0);when(staff.findIdentityById(9L)).thenReturn(Optional.of(actor));when(roles.findRoleCodesByStaffId(9L)).thenReturn(List.of("KNOWLEDGE"));service=new KnowledgeRevisionService(rows,staff,roles,stores,logs);
        tx.execute(s->{assertEquals(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class),template.getMapper(Probe.class).id());assertEquals("READ-COMMITTED",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));return null;});
    }
    interface Probe {@org.apache.ibatis.annotations.Select("SELECT CONNECTION_ID()") Long id();}
    @AfterEach void close(){TenantManager.setTenantFactory(previous);if(created){server.execute("DROP DATABASE `"+schema+"`");created=false;}}
    Content content(String title,String audience,String risk){var today=KnowledgeRevisionService.today();return new Content(title,"虚构软件流程摘要","玉米","大庆","苗期","测试适用范围","测试不适用范围","仅用于软件验收\n第二行","虚构来源，不作农技指导",audience,risk,today,today.plusDays(30));}
    KnowledgeRevisionService.Detail create(String key,String group,int latest,Content c){return tx.execute(s->service.create(9L,group,new KnowledgeRevisionService.Draft(key,1L,latest,c)));}
    KnowledgeRevisionService.Detail change(String key,int revision,String version,String action){return tx.execute(s->service.change(9L,key,revision,version,action,"虚构验收决定"));}
    String publish(String title,String audience,String risk){String key=UUID.randomUUID().toString();create(key,null,0,content(title,audience,risk));change(key,1,"1","REVIEW");change(key,1,"2","PUBLISH");return key;}
    @Test void migrationCreatesOneEmptyVersionSourceAndRealPermissionsWithoutRoleGrants(){assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_knowledge_revision",Integer.class));assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE permission LIKE 'agronomy:knowledge:%'",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu",Integer.class));}
    @Test void concurrentOriginalCreationHasOneContentAndOneAudit()throws Exception{
        String key=UUID.randomUUID().toString();var c=content("两行草稿","PUBLIC","LOW");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();return create(key,null,0,c);});var b=pool.submit(()->{start.await();return create(key,null,0,c);});start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS).id(),b.get(10,TimeUnit.SECONDS).id());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_knowledge_revision",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log",Integer.class));assertEquals(c.body(),rows.find(key,1,false).orElseThrow().content().body());}
        finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void failedAuditRollsBackNewPublicationAndItsOldVersionWithdrawal(){
        String key=publish("原版本","PUBLIC","LOW"),next=UUID.randomUUID().toString();create(next,key,1,content("新版本","PUBLIC","LOW"));change(key,2,"1","REVIEW");int before=jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log",Integer.class);
        var failure=mock(SysOperationLogRepository.class);doThrow(new IllegalStateException("test audit unavailable")).when(failure).save(any());var broken=new KnowledgeRevisionService(rows,staff,roles,stores,failure);
        assertThrows(IllegalStateException.class,()->tx.execute(s->broken.change(9L,key,2,"2","PUBLISH","验收失败")));
        assertEquals("PUBLISHED",rows.find(key,1,false).orElseThrow().state());assertEquals("REVIEWED",rows.find(key,2,false).orElseThrow().state());assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log",Integer.class));
    }
    @Test void concurrentAppendReplaysSameRequestAndRejectsDifferentStaleDrafts()throws Exception{
        String key=UUID.randomUUID().toString(),same=UUID.randomUUID().toString();create(key,null,0,content("首版","PUBLIC","LOW"));var c=content("追加草稿","PUBLIC","LOW");
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();return create(same,key,1,c);});var b=pool.submit(()->{start.await();return create(same,key,1,c);});start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS).id(),b.get(10,TimeUnit.SECONDS).id());assertEquals(2,rows.latestRevision(key));
            var second=new CountDownLatch(1);var jobs=new ArrayList<Future<Boolean>>();for(int n=0;n<2;n++){String request=UUID.randomUUID().toString();jobs.add(pool.submit(()->{second.await();try{create(request,key,2,c);return true;}catch(IllegalArgumentException stale){return false;}}));}second.countDown();int success=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))success++;assertEquals(1,success);assertEquals(3,rows.latestRevision(key));assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log",Integer.class));
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void concurrentDifferentReviewedVersionsFinishWithOnlyNewestPublished()throws Exception{
        String key=UUID.randomUUID().toString();create(key,null,0,content("旧审核","PUBLIC","LOW"));change(key,1,"1","REVIEW");create(UUID.randomUUID().toString(),key,1,content("新审核","PUBLIC","LOW"));change(key,2,"1","REVIEW");var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var jobs=new ArrayList<Future<Boolean>>();for(int version=1;version<=2;version++){int target=version;jobs.add(pool.submit(()->{start.await();try{change(key,target,"2","PUBLISH");return true;}catch(IllegalArgumentException stale){return false;}}));}start.countDown();int success=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))success++;assertTrue(success>=1);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_knowledge_revision WHERE state='PUBLISHED'",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT revision FROM agronomy_knowledge_revision WHERE state='PUBLISHED'",Integer.class));assertEquals("新审核",new PublicKnowledgeService(rows).detail(key,2,"大庆","玉米","苗期").article().title());}
        finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void publicSqlFiltersScopeAudienceDatesStoreAndLiteralSearchBeforePaging(){
        String valid=publish("测试50%_正文","PUBLIC","LOW"),internal=publish("内部","INTERNAL","REQUIRES_HUMAN"),expired=publish("过期","PUBLIC","LOW"),future=publish("未来","PUBLIC","LOW"),withdrawn=publish("撤回","PUBLIC","LOW");
        String draft=UUID.randomUUID().toString();create(draft,null,0,content("草稿","PUBLIC","LOW"));change(withdrawn,1,"3","WITHDRAW");
        var today=KnowledgeRevisionService.today();jdbc.update("UPDATE agronomy_knowledge_revision SET effective_until=? WHERE logical_key=?",today.minusDays(1),expired);jdbc.update("UPDATE agronomy_knowledge_revision SET effective_from=? WHERE logical_key=?",today.plusDays(1),future);
        var scope=new Scope("大庆","玉米","苗期");assertEquals(List.of(valid),rows.publicPage(scope,today,null,null).stream().map(r->r.logicalKey()).toList());assertEquals(List.of(scope),rows.publicScopes(today));assertEquals(1,rows.publicPage(scope,today,null,"%_").size());assertTrue(rows.publicPage(new Scope("外地","玉米","苗期"),today,null,null).isEmpty());assertTrue(rows.publicCurrent(internal,scope,today).isEmpty());assertTrue(rows.publicCurrent(draft,scope,today).isEmpty());
        for(var mismatch:List.of(new Scope("其他地区","玉米","苗期"),new Scope("大庆","水稻","苗期"),new Scope("大庆","玉米","其他阶段"))){assertTrue(rows.publicPage(mismatch,today,null,null).isEmpty());assertTrue(rows.publicCurrent(valid,mismatch,today).isEmpty());}
        var visible=rows.publicPage(scope,today,null,null).get(0);assertTrue(rows.publicPage(scope,today,visible.id(),null).isEmpty());jdbc.update("UPDATE agronomy_knowledge_revision SET effective_until=? WHERE logical_key=?",today,valid);assertEquals(1,rows.publicPage(scope,today,null,null).size(),"适用期限最后一天仍有效");jdbc.update("UPDATE store SET status=0 WHERE id=1");assertTrue(rows.publicScopes(today).isEmpty());assertTrue(rows.publicPage(scope,today,null,null).isEmpty());
    }
}
