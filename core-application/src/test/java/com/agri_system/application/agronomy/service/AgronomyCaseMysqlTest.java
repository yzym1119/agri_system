package com.agri_system.application.agronomy.service;

import com.agri_system.application.config.JdbcAccessConfig;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import com.agri_system.infrastructure.agronomy.repository.AgronomyCaseRepositoryImpl;
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
class AgronomyCaseMysqlTest {
    JdbcTemplate server,jdbc;String schema;boolean created;TenantFactory previous;
    TransactionTemplate tx;AgronomyCaseService service;AgronomyCaseRepositoryImpl rows;
    StaffRepository staff;SysRoleRepository roles;StaffAuthorityService authority;
    StorePublicationRepositoryImpl stores;SysOperationLogRepository logs;
    Facts facts=new Facts("虚构地区","虚构作物","不清楚");
    @BeforeEach void setup()throws Exception{
        previous=TenantManager.getTenantFactory();TenantManager.setTenantFactory(null);
        String base="jdbc:mysql://"+System.getProperty("mysql.it.host","127.0.0.1")+":"+System.getProperty("mysql.it.port","3306")+"/";
        String options="?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        String user=System.getProperty("mysql.it.user","root"),password=System.getProperty("mysql.it.password","");
        server=new JdbcTemplate(new DriverManagerDataSource(base+options,user,password));
        schema="agri_case_it_"+UUID.randomUUID().toString().replace("-","");server.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");created=true;
        var source=new DriverManagerDataSource(base+schema+options,user,password);var init=new JdbcTemplate(source);
        init.execute("CREATE TABLE staff(id BIGINT PRIMARY KEY)");
        try(var c=source.getConnection()){
            for(String file:List.of("V1__store.sql","V46__staff_rbac_and_log.sql"))ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/"+file));
            init.execute("ALTER TABLE store ADD deleted INT NOT NULL DEFAULT 0");
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V147__client_store_publication.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V149__agronomy_case.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V150__agronomy_assistant_processing.sql"));
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V151__agronomy_assistant_steps.sql"));
        }
        init.update("INSERT INTO store(id,code,name,client_visible) VALUES(1,'VIRTUAL','虚构咨询门店',1),(2,'OTHER','其他虚构门店',1)");
        var config=new FlexConfiguration(new Environment("case",new SpringManagedTransactionFactory(),new FlexDataSource("case",source,false)));config.setMapUnderscoreToCamelCase(true);
        var factory=new FlexSqlSessionFactoryBuilder().build(config);config.addMapper(SysOperationLogMapper.class);config.addMapper(Probe.class);
        var template=new SqlSessionTemplate(factory);jdbc=new JdbcTemplate(config.getEnvironment().getDataSource());tx=new TransactionTemplate(new JdbcAccessConfig().transactionManager(factory));tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        rows=new AgronomyCaseRepositoryImpl(jdbc);stores=new StorePublicationRepositoryImpl(jdbc);logs=new SysOperationLogRepositoryImpl(template.getMapper(SysOperationLogMapper.class));
        staff=mock(StaffRepository.class);roles=mock(SysRoleRepository.class);authority=mock(StaffAuthorityService.class);
        for(long id:List.of(9L,10L,11L)){var s=new Staff();s.setId(id);s.setStoreId(id==11?2L:1L);s.setStatus(1);s.setDeleted(0);when(staff.findIdentityById(id)).thenReturn(Optional.of(s));when(roles.findRoleCodesByStaffId(id)).thenReturn(List.of("SALE"));}
        when(authority.loadPermissions(9L)).thenReturn(List.of("agronomy:case:query","agronomy:case:manage","agronomy:case:review"));when(authority.loadPermissions(10L)).thenReturn(List.of("agronomy:case:query"));
        service=new AgronomyCaseService(rows,staff,roles,authority,stores,logs);
        tx.execute(s->{assertEquals(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class),template.getMapper(Probe.class).id());assertEquals("READ-COMMITTED",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));return null;});
    }
    interface Probe {@org.apache.ibatis.annotations.Select("SELECT CONNECTION_ID()") Long id();}
    @AfterEach void close(){TenantManager.setTenantFactory(previous);if(created){server.execute("DROP DATABASE `"+schema+"`");created=false;}}
    AgronomyCaseService.Receipt create(long customer,long store,String key){return tx.execute(s->service.create(customer,new AgronomyCaseService.Create(key,store,"虚构咨询，仅测试软件流程，如何记录积水",facts)));}
    AgronomyCaseService.Receipt supplement(String id,String key,String version){return tx.execute(s->service.changeCustomer(101L,Long.valueOf(id),"SUPPLEMENT",new AgronomyCaseService.Change(key,version,"补充虚构情况",null,facts,null)));}
    @Test void concurrentOriginalCreateTenRequestsLeaveOneProblemAndOneOriginalEntry()throws Exception{
        String key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(10);var start=new CountDownLatch(1);
        try{var jobs=new ArrayList<Future<AgronomyCaseService.Receipt>>();for(int n=0;n<10;n++)jobs.add(pool.submit(()->{start.await();return create(101,1,key);}));start.countDown();var expected=jobs.get(0).get(15,TimeUnit.SECONDS);for(var job:jobs)assertEquals(expected,job.get(15,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));assertEquals(expected,service.submissionCustomer(101L,key).receipt());
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void concurrentSameAppendReplaysButDifferentOldConfirmationsHaveOnlyOneWinner()throws Exception{
        String id=create(101,1,UUID.randomUUID().toString()).caseId(),key=UUID.randomUUID().toString();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{var a=pool.submit(()->{start.await();return supplement(id,key,"1");});var b=pool.submit(()->{start.await();return supplement(id,key,"1");});start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            var gate=new CountDownLatch(1);var jobs=new ArrayList<Future<Boolean>>();for(int n=0;n<2;n++){String next=UUID.randomUUID().toString();jobs.add(pool.submit(()->{gate.await();try{supplement(id,next,"2");return true;}catch(IllegalArgumentException stale){return false;}}));}gate.countDown();int winners=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            var detail=service.detailCustomer(101L,Long.valueOf(id),null);assertEquals("3",detail.problem().version());assertEquals("3",detail.problem().factsVersion());assertEquals(3,detail.entries().size());
        }finally{start.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void mybatisAuditAndJdbcReplyReallyRollbackOnSameFlexConnection(){
        String id=create(101,1,UUID.randomUUID().toString()).caseId();var brokenLog=mock(SysOperationLogRepository.class);
        doAnswer(i->{logs.save(i.getArgument(0));throw new IllegalStateException("test after MyBatis insert");}).when(brokenLog).save(any());
        var broken=new AgronomyCaseService(rows,staff,roles,authority,stores,brokenLog);String key=UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class,()->tx.execute(s->broken.changeStaff(9L,Long.valueOf(id),"HUMAN_REPLY",new AgronomyCaseService.Change(key,"1","不应留下的虚构回复","测试来源",null,null))));
        assertEquals("1",service.detailCustomer(101L,Long.valueOf(id),null).problem().version());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log",Integer.class));assertEquals("UNKNOWN",service.submissionStaff(9L,key).status());
    }
    @Test void sameUuidAcrossCreationAndLaterMutationCannotCreateOrAppendAnotherFact(){
        String original=UUID.randomUUID().toString();String id=create(101,1,original).caseId();String later=UUID.randomUUID().toString();supplement(id,later,"1");
        assertThrows(IllegalArgumentException.class,()->create(101,1,later));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case",Integer.class));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
        assertThrows(IllegalArgumentException.class,()->supplement(id,original,"2"));
    }
    @Test void sqlFiltersOwnersStoresAssignmentsAndStatusBeforeBoundedPagination(){
        for(int n=0;n<25;n++){create(101,1,UUID.randomUUID().toString());create(202,1,UUID.randomUUID().toString());create(303,2,UUID.randomUUID().toString());}
        var first=service.pageCustomer(101L,null,null);assertEquals(20,first.rows().size());assertNotNull(first.nextAfter());var second=service.pageCustomer(101L,Long.valueOf(first.nextAfter()),null);assertEquals(5,second.rows().size());assertNull(second.nextAfter());
        var all=new HashSet<String>();first.rows().forEach(x->assertTrue(all.add(x.id())));second.rows().forEach(x->assertTrue(all.add(x.id())));assertTrue(service.pageCustomer(101L,null,"WAITING_REVIEW").rows().isEmpty());
        assertEquals(20,service.pageStaff(9L,null,null,null).rows().size());assertTrue(service.pageStaff(10L,null,null,null).rows().isEmpty());assertThrows(IllegalArgumentException.class,()->service.pageStaff(9L,2L,null,null));
        String id=first.rows().get(0).id();tx.execute(s->service.changeStaff(9L,Long.valueOf(id),"ASSIGN",new AgronomyCaseService.Change(UUID.randomUUID().toString(),"1","",null,null,10L)));
        assertEquals(1,service.pageStaff(10L,null,null,null).rows().size());assertEquals(1,service.detailCustomer(101L,Long.valueOf(id),null).entries().size());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM sys_menu WHERE permission LIKE 'agronomy:case:%'",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_menu WHERE menu_id IN (SELECT id FROM sys_menu WHERE permission LIKE 'agronomy:case:%')",Integer.class));
    }
    @Test void entryPagingExcludesAssignmentBeforeLimitAndDoesNotMixNewerVersionIntoOldSnapshot(){
        String id=create(101,1,UUID.randomUUID().toString()).caseId();for(int n=1;n<=24;n++)supplement(id,UUID.randomUUID().toString(),Integer.toString(n));
        tx.execute(s->service.changeStaff(9L,Long.valueOf(id),"ASSIGN",new AgronomyCaseService.Change(UUID.randomUUID().toString(),"25","",null,null,10L)));
        var first=service.detailCustomer(101L,Long.valueOf(id),null);assertEquals(20,first.entries().size());assertEquals("25",first.entries().get(0).version());assertEquals("26",first.problem().version());
        assertEquals(5,service.detailCustomer(101L,Long.valueOf(id),Long.valueOf(first.nextAfter())).entries().size());
        assertEquals(1,rows.entries(Long.valueOf(id),true,1,null).size(),"旧主档只配旧事实，不混入后来追加");
    }
    @Test void directHumanQuestionIsOneAtomicWaitingCaseAndOneImmutableFirstEntry() {
        String key=UUID.randomUUID().toString();var command=new AgronomyCaseService.Create(key,1L,"请门店直接处理，纯软件验收",facts,true);
        var first=tx.execute(s->service.create(101L,command));assertEquals("WAITING_REVIEW",first.status());assertEquals("1",first.version());
        assertEquals(first,tx.execute(s->service.create(101L,command)));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
        assertEquals("WAITING_REVIEW",service.pageStaff(9L,null,null,"WAITING_REVIEW").rows().get(0).problem().status());
    }
    @Test void latestReplySqlUsesOriginalCaseAndSnapshotInsteadOfFirstTwentyHistoryRows() {
        String id=create(101,1,UUID.randomUUID().toString()).caseId();
        tx.execute(s->service.changeStaff(9L,Long.valueOf(id),"HUMAN_REPLY",new AgronomyCaseService.Change(UUID.randomUUID().toString(),"1","虚构旧答复","软件验收来源",null,null)));
        for(int v=2;v<=26;v++)supplement(id,UUID.randomUUID().toString(),Integer.toString(v));
        var detail=service.detailCustomer(101L,Long.valueOf(id),null);assertEquals(20,detail.entries().size());assertEquals("虚构旧答复",detail.latestReply().body());
        tx.execute(s->service.changeStaff(9L,Long.valueOf(id),"HUMAN_REPLY",new AgronomyCaseService.Change(UUID.randomUUID().toString(),"27","虚构新答复","软件验收来源",null,null)));
        assertEquals("虚构旧答复",rows.latestHumanReply(Long.valueOf(id),27).orElseThrow().body());
        assertEquals("虚构新答复",service.detailCustomer(101L,Long.valueOf(id),null).latestReply().body());assertTrue(rows.latestHumanReply(Long.valueOf(id),1).isEmpty());
        String other=create(202,1,UUID.randomUUID().toString()).caseId();assertNull(service.detailCustomer(202L,Long.valueOf(other),null).latestReply());
    }

    com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy assistantPolicy(long calls) {
        return new com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy(true,calls,calls,100000,"deepseek-flash",true,1024,300000,1200000,750,"TEST_PRICE");
    }
    AgronomyAssistantCoordinator assistant(com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy policy,
            com.agri_system.domain.agronomy.assistant.AgronomyAssistantWorkflow work,
            com.agri_system.domain.agronomy.assistant.AgronomyAssistantRepository assistantRows) {
        var customers=mock(com.agri_system.domain.customer.repository.CustomerRepository.class);
        when(customers.findAuthStateById(anyLong())).thenAnswer(a->Optional.of(new com.agri_system.domain.customer.model.CustomerAuthState(a.getArgument(0),1,0,1L)));
        return new AgronomyAssistantCoordinator(rows,assistantRows,stores,customers,tx.getTransactionManager(),new com.fasterxml.jackson.databind.ObjectMapper(),java.time.Clock.fixed(java.time.Instant.parse("2026-11-11T03:00:00Z"),java.time.ZoneOffset.UTC),policy,work);
    }
    @Test void assistantMysqlOneOriginalTenRacesUsesSameFlexConnectionAndOutsideWorkflow() throws Exception {
        long id=Long.parseLong(create(101,1,UUID.randomUUID().toString()).caseId());String key=UUID.randomUUID().toString();
        var metadata=spy(new com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl(jdbc));rows=spy(rows);
        var connectionAtInitialize=new java.util.concurrent.atomic.AtomicReference<Long>();var connectionAtUpdate=new java.util.concurrent.atomic.AtomicReference<Long>();
        doAnswer(a->{assertEquals("READ-COMMITTED",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));connectionAtInitialize.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));return a.callRealMethod();}).when(metadata).initialize(any());
        doAnswer(a->{connectionAtUpdate.set(jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class));return a.callRealMethod();}).when(rows).update(anyLong(),anyLong(),any(),anyString(),anyLong(),nullable(Long.class),any());
        var calls=new java.util.concurrent.atomic.AtomicInteger();var c=assistant(assistantPolicy(10),(snapshot,usage)->{
            assertNull(com.mybatisflex.core.transaction.TransactionContext.getXID());assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());calls.incrementAndGet();return "{}";
        },metadata);
        var pool=Executors.newFixedThreadPool(10);var start=new CountDownLatch(1);var all=new ArrayList<Future<?>>();
        try {for(int n=0;n<10;n++)all.add(pool.submit(()->{start.await();return c.submit(101L,1L,id,new AgronomyAssistantCoordinator.Command(key,"1","1",true));}));start.countDown();for(var f:all)f.get(20,TimeUnit.SECONDS);}
        finally {pool.shutdownNow();}
        assertEquals(1,calls.get());assertNotNull(connectionAtInitialize.get());assertEquals(connectionAtInitialize.get(),connectionAtUpdate.get());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry WHERE type='ASSISTANT_REQUEST'",Integer.class));
        assertEquals("COMPLETED",c.read(101L,1L,id,key).state());assertEquals(11810,metadata.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void assistantMysqlDifferentCasesCannotBothConsumeFinalStoreAllowance() throws Exception {
        long a=Long.parseLong(create(101,1,UUID.randomUUID().toString()).caseId()),b=Long.parseLong(create(202,1,UUID.randomUUID().toString()).caseId());
        var metadata=new com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl(jdbc);var calls=new java.util.concurrent.atomic.AtomicInteger();
        var c=assistant(assistantPolicy(1),(s,usage)->{calls.incrementAndGet();return "{}";},metadata);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {var all=new ArrayList<Future<Boolean>>();for(long id:new long[]{a,b})all.add(pool.submit(()->{start.await();try{c.submit(id==a?101L:202L,1L,id,new AgronomyAssistantCoordinator.Command(UUID.randomUUID().toString(),"1","1",true));return true;}catch(IllegalArgumentException rejected){return false;}}));start.countDown();int accepted=0;for(var f:all)if(f.get(20,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);}
        finally {pool.shutdownNow();}
        assertEquals(1,calls.get());assertEquals(1,metadata.usage(1L,java.time.LocalDate.of(2026,11,11)).modelCalls());
        assertEquals(3,jdbc.queryForObject("SELECT SUM(version) FROM agronomy_case WHERE id IN (?,?)",Integer.class,a,b));
    }
    @Test void assistantMysqlMetadataFailureRollsBackEntryReservationAndCaseVersion() {
        long id=Long.parseLong(create(101,1,UUID.randomUUID().toString()).caseId());String key=UUID.randomUUID().toString();
        var metadata=spy(new com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl(jdbc));
        doAnswer(a->{a.callRealMethod();throw new IllegalStateException("软件验收回滚");}).when(metadata).initialize(any());
        var c=assistant(assistantPolicy(2),(s,usage)->{throw new AssertionError();},metadata);
        assertThrows(RuntimeException.class,()->c.submit(101L,1L,id,new AgronomyAssistantCoordinator.Command(key,"1","1",true)));
        assertTrue(rows.findRequest("CUSTOMER",101L,key).isEmpty());assertEquals(1,rows.find(id,false).orElseThrow().version());
        assertEquals(0,metadata.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void assistantMysqlUnknownSurvivesNewCoordinatorAndDatabaseRejectsNegativeReserve() {
        long id=Long.parseLong(create(101,1,UUID.randomUUID().toString()).caseId());String key=UUID.randomUUID().toString();var count=new java.util.concurrent.atomic.AtomicInteger();
        var metadata=new com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl(jdbc);var c=assistant(assistantPolicy(2),(s,usage)->{count.incrementAndGet();throw new IllegalStateException("测试未知");},metadata);
        var command=new AgronomyAssistantCoordinator.Command(key,"1","1",true);assertEquals("UNKNOWN",c.submit(101L,1L,id,command).state());
        assertEquals("UNKNOWN",assistant(assistantPolicy(2),(s,usage)->{throw new AssertionError();},metadata).submit(101L,1L,id,command).state());assertEquals(1,count.get());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE agronomy_case_entry SET assistant_model_reserved_microusd=-1 WHERE request_key=?",key));
        assertEquals(11810,metadata.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void assistantMysqlStagesCommitBeforeOutsideCallsAndKeepPartialUsageAfterUnknown() throws Exception {
        long id=Long.parseLong(create(101,1,UUID.randomUUID().toString()).caseId());String key=UUID.randomUUID().toString();
        var metadata=spy(new com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl(jdbc));
        var writes=new java.util.concurrent.atomic.AtomicInteger();var dispatches=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(a->{assertEquals("READ-COMMITTED",jdbc.queryForObject("SELECT @@transaction_isolation",String.class));assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());writes.incrementAndGet();return a.callRealMethod();}).when(metadata).compareSteps(anyLong(),anyString(),nullable(String.class),anyString(),any());
        var c=assistant(assistantPolicy(2),(snapshot,calls)->{
            calls.begin(com.agri_system.domain.agronomy.assistant.AssistantCallLedger.Stage.SEARCH);
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());assertNull(com.mybatisflex.core.transaction.TransactionContext.getXID());
            assertTrue(jdbc.queryForObject("SELECT assistant_steps FROM agronomy_case_entry WHERE request_key=?",String.class,key).contains("STARTED"));
            dispatches.incrementAndGet();calls.succeeded(com.agri_system.domain.agronomy.assistant.AssistantCallLedger.Stage.SEARCH,0,0);
            calls.begin(com.agri_system.domain.agronomy.assistant.AssistantCallLedger.Stage.MODEL);dispatches.incrementAndGet();
            calls.unknown(com.agri_system.domain.agronomy.assistant.AssistantCallLedger.Stage.MODEL);throw new IllegalStateException("仅测试结果未知");
        },metadata);
        var command=new AgronomyAssistantCoordinator.Command(key,"1","1",true);assertEquals("UNKNOWN",c.submit(101L,1L,id,command).state());
        var entry=rows.findRequest("CUSTOMER",101L,key).orElseThrow();var steps=new com.fasterxml.jackson.databind.ObjectMapper().readTree(metadata.steps(entry.id()).orElseThrow());
        assertEquals("SUCCEEDED",steps.path("SEARCH").path("state").asText());assertEquals(750,steps.path("SEARCH").path("configuredCostMicrousd").asLong());
        assertEquals("UNKNOWN",steps.path("MODEL").path("state").asText());assertTrue(steps.path("MODEL").path("configuredCostMicrousd").isNull());
        assertEquals("UNKNOWN",c.submit(101L,1L,id,command).state());assertEquals(4,writes.get());assertEquals(2,dispatches.get());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE agronomy_case_entry SET assistant_steps='not-json' WHERE id=?",entry.id()));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE agronomy_case_entry SET assistant_steps='{}' WHERE type='QUESTION'"));
    }
    @Test void assistantMysqlStagePermitWriteFailureRollsBackBeforeAnyOutsideDispatch() {
        long id=Long.parseLong(create(101,1,UUID.randomUUID().toString()).caseId());String key=UUID.randomUUID().toString();
        var metadata=spy(new com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl(jdbc));
        doAnswer(a->{a.callRealMethod();throw new IllegalStateException("模拟阶段许可提交失败");}).when(metadata).compareSteps(anyLong(),anyString(),nullable(String.class),anyString(),any());
        var sent=new java.util.concurrent.atomic.AtomicInteger();var c=assistant(assistantPolicy(2),(s,calls)->{
            calls.begin(com.agri_system.domain.agronomy.assistant.AssistantCallLedger.Stage.SEARCH);sent.incrementAndGet();return "{}";
        },metadata);
        assertEquals("UNKNOWN",c.submit(101L,1L,id,new AgronomyAssistantCoordinator.Command(key,"1","1",true)).state());assertEquals(0,sent.get());
        assertTrue(metadata.steps(rows.findRequest("CUSTOMER",101L,key).orElseThrow().id()).isEmpty());
        assertEquals(11810,metadata.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
}
