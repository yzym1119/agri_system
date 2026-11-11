package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantWorkflow;
import com.agri_system.domain.agronomy.assistant.AssistantCallLedger;
import com.agri_system.domain.agronomy.model.AgronomyCase;
import com.agri_system.domain.agronomy.model.AgronomyCaseEntry;
import com.agri_system.domain.customer.model.CustomerAuthState;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.infrastructure.agronomy.repository.AgronomyCaseRepositoryImpl;
import com.agri_system.infrastructure.agronomy.repository.AgronomyAssistantRepositoryImpl;
import com.agri_system.infrastructure.store.repository.StorePublicationRepositoryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgronomyAssistantCoordinatorTest {
    JdbcTemplate jdbc; TransactionTemplate tx; DataSourceTransactionManager manager;
    AgronomyCaseRepositoryImpl cases; AgronomyAssistantRepositoryImpl requests;
    StorePublicationRepositoryImpl stores; CustomerRepository customers;
    com.agri_system.application.auth.service.AuthService clientAuth;
    final Clock clock=Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"),ZoneOffset.UTC);
    AssistantBudgetPolicy policy=policy(true,2,2,100000);
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource("jdbc:h2:mem:assistant_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        jdbc.execute("CREATE TABLE store(id BIGINT PRIMARY KEY,name VARCHAR(100),phone VARCHAR(30),address VARCHAR(200),status INT,deleted INT,client_visible INT,client_publication_version BIGINT)");
        jdbc.update("INSERT INTO store VALUES(1,'虚构软件门店',NULL,NULL,1,0,1,1)");
        jdbc.execute("CREATE TABLE customer(id BIGINT PRIMARY KEY,status INT,deleted INT,auth_version BIGINT)");
        jdbc.update("INSERT INTO customer VALUES(101,1,0,1),(202,1,0,1)");
        jdbc.execute("CREATE TABLE agronomy_case(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT,store_id BIGINT,request_key VARCHAR(36),request_hash VARCHAR(64),question VARCHAR(2000),region VARCHAR(100),crop VARCHAR(50),growth_stage VARCHAR(100),status VARCHAR(24),version BIGINT,facts_version BIGINT,assigned_staff_id BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(customer_id,request_key))");
        jdbc.execute("CREATE TABLE agronomy_case_entry(id BIGINT AUTO_INCREMENT PRIMARY KEY,case_id BIGINT,actor_type VARCHAR(16),actor_id BIGINT,request_key VARCHAR(36),request_hash VARCHAR(64),type VARCHAR(24),body TEXT,source_text TEXT,region VARCHAR(100),crop VARCHAR(50),growth_stage VARCHAR(100),assigned_staff_id BIGINT,expected_version BIGINT,result_version BIGINT,result_facts_version BIGINT,result_status VARCHAR(24),created_at TIMESTAMP,assistant_state VARCHAR(16),assistant_snapshot TEXT,assistant_outcome TEXT,assistant_steps TEXT,assistant_budget_day DATE,assistant_model_reserved_microusd BIGINT,assistant_search_reserved_microusd BIGINT,assistant_claim_key VARCHAR(36),assistant_updated_at TIMESTAMP,UNIQUE(actor_type,actor_id,request_key))");
        cases=new AgronomyCaseRepositoryImpl(jdbc);requests=new AgronomyAssistantRepositoryImpl(jdbc);stores=new StorePublicationRepositoryImpl(jdbc);
        clientAuth=mock(com.agri_system.application.auth.service.AuthService.class);when(clientAuth.isCurrentClientAuthorization(any())).thenReturn(true);
        customers=mock(CustomerRepository.class);
        when(customers.findAuthStateById(anyLong())).thenAnswer(a->jdbc.query("SELECT * FROM customer WHERE id=?",(r,n)->new CustomerAuthState(r.getLong("id"),r.getInt("status"),r.getInt("deleted"),r.getLong("auth_version")),a.<Long>getArgument(0)).stream().findFirst());
    }
    static AssistantBudgetPolicy policy(boolean enabled,long calls,long searches,long money) {
        return new AssistantBudgetPolicy(enabled,calls,searches,money,"deepseek-flash",true,1024,300000,1200000,750,"TEST_ONLY_PRICE_V1");
    }
    long create(long customer) {
        var at=LocalDateTime.now(clock);var f=new AgronomyCase.Facts("虚构地区","虚构作物","软件阶段");String key=UUID.randomUUID().toString();
        cases.insertCase(new AgronomyCase(null,customer,1L,key,"0".repeat(64),"仅软件流程验收，如何记录积水",f,"WAITING_REVIEW",1,1,null,at,at));
        var c=cases.findCreation(customer,key).orElseThrow();cases.insertEntry(new AgronomyCaseEntry(null,c.id(),"CUSTOMER",customer,key,"0".repeat(64),"QUESTION",c.question(),"",f,null,0,1,1,c.status(),at));return c.id();
    }
    AgronomyAssistantCoordinator coordinator(AgronomyAssistantWorkflow work) {
        return new AgronomyAssistantCoordinator(cases,requests,stores,customers,clientAuth,manager,new ObjectMapper(),clock,policy,work);
    }
    AgronomyAssistantCoordinator.Command command(String key) {return new AgronomyAssistantCoordinator.Command(key,"1","1",true);}
    @Test void originalRequestCommitsReservationBeforeOneOutsideTransactionWorkflowAndKeepsSnapshot() {
        long id=create(101);var calls=new AtomicInteger();var c=coordinator((snapshot,usage)->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("RUNNING",jdbc.queryForObject("SELECT assistant_state FROM agronomy_case_entry WHERE type='ASSISTANT_REQUEST'",String.class));
            calls.incrementAndGet();return "{\"kind\":\"STAGED_TEST_ONLY\"}";
        });String key=UUID.randomUUID().toString();
        var first=c.submit(authorization(101L,1L),id,command(key));assertEquals("COMPLETED",first.state());assertEquals(first,c.submit(authorization(101L,1L),id,command(key)));assertEquals(1,calls.get());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
        assertEquals(1,cases.entries(id,true,2,null).size());assertEquals(1,cases.entries(id,false,2,null).size());
        assertEquals("2",Long.toString(cases.find(id,false).orElseThrow().version()));
    }

    AgronomyAssistantCoordinator queued(long id,String key) {
        if(!mockingDetails(requests).isSpy())requests=spy(requests);doThrow(new IllegalStateException("模拟派出前进程中断")).when(requests).claim(anyLong(),anyString(),any());
        var c=coordinator((snapshot,usage)->{throw new AssertionError("不得执行");});assertThrows(IllegalStateException.class,()->c.submit(authorization(101L,1L),id,command(key)));
        doCallRealMethod().when(requests).claim(anyLong(),anyString(),any());return c;
    }
    @Test void defaultDisabledAndInsufficientMoneyLeaveNoRequestOrVersionChange() {
        long id=create(101);policy=AssistantBudgetPolicy.disabled();var c=coordinator((s,usage)->{throw new AssertionError();});
        assertThrows(IllegalArgumentException.class,()->c.submit(authorization(101L,1L),id,command(UUID.randomUUID().toString())));
        policy=policy(true,2,2,11809);assertEquals(11060,policy.modelReservation());
        assertThrows(IllegalArgumentException.class,()->coordinator((s,usage)->"{}").submit(authorization(101L,1L),id,command(UUID.randomUUID().toString())));
        assertEquals(1,cases.find(id,false).orElseThrow().version());assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).modelCalls());
    }
    @Test void sameStoreDifferentProblemsShareDailyCountAndReservedMoney() {
        long a=create(101),b=create(202);policy=policy(true,1,1,11810);var c=coordinator((s,usage)->"{}");
        c.submit(authorization(101L,1L),a,command(UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class,()->c.submit(authorization(202L,1L),b,command(UUID.randomUUID().toString())));
        assertEquals(1,cases.find(b,false).orElseThrow().version());assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void sameKeyCannotChangeProblemVersionConsentOrBorrowHumanRequest() {
        long a=create(101),b=create(101);String key=UUID.randomUUID().toString();var c=coordinator((s,usage)->"{}");c.submit(authorization(101L,1L),a,command(key));
        assertThrows(RuntimeException.class,()->c.submit(authorization(101L,1L),b,command(key)));
        assertThrows(RuntimeException.class,()->c.submit(authorization(101L,1L),a,new AgronomyAssistantCoordinator.Command(key,"2","1",true)));
        assertThrows(RuntimeException.class,()->c.submit(authorization(101L,1L),a,new AgronomyAssistantCoordinator.Command(key,"1","1",false)));
        String original=jdbc.queryForObject("SELECT request_key FROM agronomy_case_entry WHERE case_id=? AND type='QUESTION'",String.class,a);
        assertThrows(RuntimeException.class,()->c.submit(authorization(101L,1L),a,command(original)));
        assertThrows(RuntimeException.class,()->c.read(authorization(202L,1L),a,key));
    }
    @Test void unknownKeepsMaximumReservationAndNeverRepeatsOrAcceptsAnotherRequest() {
        long id=create(101);String key=UUID.randomUUID().toString();var calls=new AtomicInteger();var c=coordinator((s,usage)->{calls.incrementAndGet();throw new IllegalStateException("PRIVATE_QUESTION SECRET_KEY");});
        var result=c.submit(authorization(101L,1L),id,command(key));assertEquals("UNKNOWN",result.state());assertFalse(result.toString().contains("SECRET_KEY"));
        assertEquals(result,c.submit(authorization(101L,1L),id,command(key)));assertEquals(1,calls.get());
        assertThrows(RuntimeException.class,()->c.submit(authorization(101L,1L),id,new AgronomyAssistantCoordinator.Command(UUID.randomUUID().toString(),"2","1",true)));
        assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void restartDoesNotRedispatchRunningRequestAndTimeoutOnlyMarksUnknown() {
        long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator((s,usage)->{throw new AssertionError("模拟进程退出");});
        assertThrows(AssertionError.class,()->c.submit(authorization(101L,1L),id,command(key)));
        var restart=new AgronomyAssistantCoordinator(cases,requests,stores,customers,clientAuth,manager,new ObjectMapper(),Clock.offset(clock,java.time.Duration.ofSeconds(100)),policy,(s,usage)->{throw new AssertionError("不得重发");});
        assertEquals("RUNNING",restart.submit(authorization(101L,1L),id,command(key)).state());assertEquals("UNKNOWN",restart.markUnknown(authorization(101L,1L),id,key).state());
        assertEquals("UNKNOWN",restart.submit(authorization(101L,1L),id,command(key)).state());assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void lateResultCannotPublishAfterFactsOrCustomerAuthorizationChanges() {
        for(boolean revoke:new boolean[]{false,true}) {
            long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator((s,usage)->{
                if(revoke)jdbc.update("UPDATE customer SET auth_version=auth_version+1 WHERE id=101");
                else tx.execute(status->{var row=cases.find(id,true).orElseThrow();cases.update(id,row.version(),new AgronomyCase.Facts("新地区","新作物","新阶段"),row.status(),2,null,LocalDateTime.now(clock));return null;});
                return "{\"privateDraft\":\"NOT_PUBLISHED\"}";
            });
            var result=c.submit(authorization(101L,1L),id,command(key));assertEquals("STALE",result.outcomeKind());
            assertFalse(jdbc.queryForObject("SELECT assistant_outcome FROM agronomy_case_entry WHERE request_key=?",String.class,key).contains("NOT_PUBLISHED"));
        }
    }
    @Test void stoppedStoreBeforeDispatchReleasesOnlyProvablyUnsentReservation() {
        long id=create(101);String key=UUID.randomUUID().toString();queued(id,key);jdbc.update("UPDATE store SET status=0 WHERE id=1");
        var result=coordinator((s,usage)->{throw new AssertionError("不得出网");}).submit(authorization(101L,1L),id,command(key));
        assertEquals("FAILED",result.state());assertEquals("NOT_DISPATCHED",result.outcomeKind());assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void queuedSnapshotIsFrozenAndNextDayOrChangedExecutionCannotUseOldBudget() {
        long id=create(101);String key=UUID.randomUUID().toString();queued(id,key);
        var next=new AgronomyAssistantCoordinator(cases,requests,stores,customers,clientAuth,manager,new ObjectMapper(),Clock.offset(clock,java.time.Duration.ofDays(1)),policy,(s,usage)->{throw new AssertionError();});
        assertEquals("FAILED",next.submit(authorization(101L,1L),id,command(key)).state());assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
        long other=create(101);String second=UUID.randomUUID().toString();queued(other,second);policy=AssistantBudgetPolicy.disabled();
        assertEquals("FAILED",coordinator((s,usage)->{throw new AssertionError();}).submit(authorization(101L,1L),other,command(second)).state());
    }
    @Test void completedOriginalSurvivesConfigurationChangeAndSnapshotTamperingFailsClosed() {
        long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator((s,usage)->"{}");var first=c.submit(authorization(101L,1L),id,command(key));
        policy=AssistantBudgetPolicy.disabled();assertEquals(first,coordinator((s,usage)->{throw new AssertionError();}).submit(authorization(101L,1L),id,command(key)));
        jdbc.update("UPDATE agronomy_case_entry SET assistant_snapshot=REPLACE(assistant_snapshot,'仅软件流程验收','被改写的原问题') WHERE request_key=?",key);
        assertThrows(RuntimeException.class,()->c.read(authorization(101L,1L),id,key));
    }
    @Test void wrongClaimCannotCompleteAnotherExecutionAndMetadataFailureRollsBackEverything() {
        long id=create(101);String key=UUID.randomUUID().toString();coordinator((s,usage)->{throw new AssertionError();});
        assertThrows(AssertionError.class,()->coordinator((s,usage)->{throw new AssertionError();}).submit(authorization(101L,1L),id,command(key)));
        long entry=cases.findRequest("CUSTOMER",101L,key).orElseThrow().id();assertFalse(requests.finish(entry,UUID.randomUUID().toString(),"COMPLETED","{}",LocalDateTime.now(clock)));
        assertEquals("RUNNING",requests.find(entry).orElseThrow().state());
        long other=create(202);requests=spy(requests);doThrow(new IllegalStateException("测试元数据失败")).when(requests).initialize(any());
        String failed=UUID.randomUUID().toString();assertThrows(RuntimeException.class,()->coordinator((s,usage)->"{}").submit(authorization(202L,1L),other,command(failed)));
        assertTrue(cases.findRequest("CUSTOMER",202L,failed).isEmpty());assertEquals(1,cases.find(other,false).orElseThrow().version());
    }
    @Test void noOuterTransactionAndStaleAuthorizationCanReachTheWorkflow() {
        long id=create(101);var c=coordinator((s,usage)->{throw new AssertionError();});
        assertThrows(RuntimeException.class,()->tx.execute(status->c.submit(authorization(101L,1L),id,command(UUID.randomUUID().toString()))));
        assertThrows(RuntimeException.class,()->c.submit(authorization(101L,2L),id,command(UUID.randomUUID().toString())));
        assertEquals(1,cases.find(id,false).orElseThrow().version());
    }
    @Test void concurrentSameOriginalOnlyGetsOneDispatch() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var calls=new AtomicInteger();var start=new java.util.concurrent.CountDownLatch(1);
        var c=coordinator((s,usage)->{calls.incrementAndGet();return "{}";});var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {var all=new java.util.ArrayList<java.util.concurrent.Future<?>>();for(int i=0;i<8;i++)all.add(pool.submit(()->{start.await();return c.submit(authorization(101L,1L),id,command(key));}));start.countDown();for(var f:all)f.get(10,java.util.concurrent.TimeUnit.SECONDS);assertEquals(1,calls.get());assertEquals("COMPLETED",c.read(authorization(101L,1L),id,key).state());}
        finally{pool.shutdownNow();}
    }

    @Test void midnightBetweenBudgetReadAndInsertCannotMoveReservationIntoUncheckedDay() {
        policy=policy(true,1,1,100000);long a=create(101),b=create(202);
        Clock tomorrow=Clock.fixed(Instant.parse("2026-11-11T16:00:00Z"),ZoneOffset.UTC);
        var first=new AgronomyAssistantCoordinator(cases,requests,stores,customers,clientAuth,manager,new ObjectMapper(),tomorrow,policy,(s,usage)->"{}");
        first.submit(authorization(101L,1L),a,command(UUID.randomUUID().toString()));
        Clock edge=new Clock(){final AtomicInteger reads=new AtomicInteger();public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(java.time.ZoneId zone){return this;}public Instant instant(){return reads.getAndIncrement()==0?Instant.parse("2026-11-11T15:59:59.999Z"):Instant.parse("2026-11-11T16:00:00Z");}};
        var calls=new AtomicInteger();var second=new AgronomyAssistantCoordinator(cases,requests,stores,customers,clientAuth,manager,new ObjectMapper(),edge,policy,(s,usage)->{calls.incrementAndGet();return "{}";});
        var result=second.submit(authorization(202L,1L),b,command(UUID.randomUUID().toString()));
        assertEquals("FAILED",result.state());assertEquals(0,calls.get());assertEquals(1,requests.usage(1L,java.time.LocalDate.of(2026,11,12)).modelCalls());
    }
    String stepJson(String key) {
        long id=cases.findRequest("CUSTOMER",101L,key).orElseThrow().id();return requests.steps(id).orElse("{}");
    }
    @Test void eachDispatchIsDurableBeforeOutsideCallAndSuccessKeepsOriginalRates() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var mapper=new ObjectMapper();
        var c=coordinator((s,calls)->{
            calls.begin(AssistantCallLedger.Stage.SEARCH);
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(stepJson(key).contains("STARTED"));calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
            calls.begin(AssistantCallLedger.Stage.MODEL);calls.succeeded(AssistantCallLedger.Stage.MODEL,100,40);return "{}";
        });
        assertEquals("COMPLETED",c.submit(authorization(101L,1L),id,command(key)).state());
        var steps=mapper.readTree(stepJson(key));assertEquals("SUCCEEDED",steps.path("SEARCH").path("state").asText());
        assertEquals(750,steps.path("SEARCH").path("configuredCostMicrousd").asLong());
        assertEquals(78,steps.path("MODEL").path("configuredCostMicrousd").asLong());
        assertEquals(100,steps.path("MODEL").path("inputTokens").asLong());
        assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void aStageCannotBeSentTwiceOrStartModelBeforeRequiredSearch() {
        for(boolean duplicate:new boolean[]{false,true}) {
            long id=create(101);String key=UUID.randomUUID().toString();
            var c=coordinator((s,calls)->{
                if(duplicate){calls.begin(AssistantCallLedger.Stage.SEARCH);calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);}
                calls.begin(duplicate?AssistantCallLedger.Stage.SEARCH:AssistantCallLedger.Stage.MODEL);throw new AssertionError("第二次派出不应发生");
            });
            assertEquals("UNKNOWN",c.submit(authorization(101L,1L),id,command(key)).state());
            assertFalse(stepJson(key).contains("MODEL"));
        }
    }
    @Test void searchSuccessSurvivesUnknownModelAndRestartCannotResendEitherStage() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var callsCount=new AtomicInteger();
        var work=(AgronomyAssistantWorkflow)(s,calls)->{
            calls.begin(AssistantCallLedger.Stage.SEARCH);callsCount.incrementAndGet();calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
            calls.begin(AssistantCallLedger.Stage.MODEL);callsCount.incrementAndGet();calls.unknown(AssistantCallLedger.Stage.MODEL);throw new IllegalStateException("仅测试未知");
        };
        assertEquals("UNKNOWN",coordinator(work).submit(authorization(101L,1L),id,command(key)).state());
        var steps=new ObjectMapper().readTree(stepJson(key));assertEquals("SUCCEEDED",steps.path("SEARCH").path("state").asText());
        assertEquals("UNKNOWN",steps.path("MODEL").path("state").asText());assertTrue(steps.path("MODEL").path("configuredCostMicrousd").isNull());
        assertEquals("UNKNOWN",coordinator(work).submit(authorization(101L,1L),id,command(key)).state());assertEquals(2,callsCount.get());
    }
    @Test void factsAuthorizationOrStoreChangedBetweenStagesStopsFurtherPaidDispatch() {
        for(int change=0;change<3;change++) {
            jdbc.update("UPDATE customer SET auth_version=1 WHERE id=101");jdbc.update("UPDATE store SET status=1 WHERE id=1");
            long id=create(101);String key=UUID.randomUUID().toString();int action=change;policy=policy(true,20,20,1000000);
            var c=coordinator((s,calls)->{
                calls.begin(AssistantCallLedger.Stage.SEARCH);calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
                if(action==0)jdbc.update("UPDATE agronomy_case SET facts_version=facts_version+1,version=version+1 WHERE id=?",id);
                if(action==1)jdbc.update("UPDATE customer SET auth_version=2 WHERE id=101");
                if(action==2)jdbc.update("UPDATE store SET status=0 WHERE id=1");
                calls.begin(AssistantCallLedger.Stage.MODEL);throw new AssertionError("不得生成");
            });
            assertEquals("STALE",c.submit(authorization(101L,1L),id,command(key)).outcomeKind());assertFalse(stepJson(key).contains("MODEL"));
        }
    }
    @Test void processExitAfterStagePermitLeavesStartedAndRestartNeverDispatchesAgain() {
        long id=create(101);String key=UUID.randomUUID().toString();
        assertThrows(AssertionError.class,()->coordinator((s,calls)->{calls.begin(AssistantCallLedger.Stage.SEARCH);throw new AssertionError("模拟退出");}).submit(authorization(101L,1L),id,command(key)));
        assertTrue(stepJson(key).contains("STARTED"));
        assertEquals("RUNNING",coordinator((s,calls)->{throw new AssertionError("不得重派");}).submit(authorization(101L,1L),id,command(key)).state());
    }
    @Test void invalidModelUsageCannotInventActualCostAndWrongClaimCannotWriteSteps() {
        long id=create(101);String key=UUID.randomUUID().toString();
        assertEquals("UNKNOWN",coordinator((s,calls)->{
            calls.begin(AssistantCallLedger.Stage.SEARCH);calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
            calls.begin(AssistantCallLedger.Stage.MODEL);calls.succeeded(AssistantCallLedger.Stage.MODEL,32769,10);return "{}";
        }).submit(authorization(101L,1L),id,command(key)).state());
        assertTrue(stepJson(key).contains("STARTED"));assertFalse(stepJson(key).contains("32769"));
        var entry=cases.findRequest("CUSTOMER",101L,key).orElseThrow();String original=stepJson(key);
        assertFalse(requests.compareSteps(entry.id(),UUID.randomUUID().toString(),original,"{}",LocalDateTime.now(clock)));assertEquals(original,stepJson(key));
    }
    @Test void localClarificationOrHumanAdviceDoesNotBurnPaidCallQuota() {
        long id=create(101);jdbc.update("UPDATE agronomy_case SET question='虚构未知人工问题' WHERE id=?",id);policy=policy(true,1,1,11810);
        var work=new ControlledAgronomyAssistantWorkflow(new ObjectMapper(),clock,policy.execution(),q->{throw new AssertionError("不得搜索");},i->{throw new AssertionError("不得生成");});
        assertEquals("COMPLETED",coordinator(work).submit(authorization(101L,1L),id,command(UUID.randomUUID().toString())).state());
        assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).modelCalls());
        assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void tamperedSuccessfulStepCostMustNotGrantModelDispatch() {
        long id=create(101);String key=UUID.randomUUID().toString();
        var result=coordinator((s,calls)->{
            calls.begin(AssistantCallLedger.Stage.SEARCH);calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
            jdbc.update("UPDATE agronomy_case_entry SET assistant_steps=REPLACE(assistant_steps,'750','0') WHERE request_key=?",key);
            calls.begin(AssistantCallLedger.Stage.MODEL);throw new AssertionError("损坏用量不能派出模型");
        });
        assertThrows(IllegalStateException.class,()->result.submit(authorization(101L,1L),id,command(key)));
    }
    @Test void oldSnapshotWithoutProcessingPlanKeepsOriginalPaidReservationAndReceipt() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator((s,calls)->"{}");c.submit(authorization(101L,1L),id,command(key));
        var mapper=new ObjectMapper();var entry=cases.findRequest("CUSTOMER",101L,key).orElseThrow();
        var old=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(requests.find(entry.id()).orElseThrow().snapshot());old.remove("processingPlan");String saved=mapper.writeValueAsString(old);
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(java.util.List.of("ASSISTANT_REQUEST",id,"1","1",true,saved))));
        jdbc.update("UPDATE agronomy_case_entry SET assistant_snapshot=?,request_hash=? WHERE id=?",saved,hash,entry.id());
        assertEquals("COMPLETED",c.submit(authorization(101L,1L),id,command(key)).state());assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void realControlledWorkflowPreservesSuccessfulSearchBeforeUnknownModel() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var count=new AtomicInteger();
        var work=new ControlledAgronomyAssistantWorkflow(new ObjectMapper(),clock,policy.execution(),q->java.util.List.of(
                new com.agri_system.domain.agronomy.assistant.AgronomySearchGateway.Candidate("软件验收资料","https://www.moa.gov.cn/test-fixture", "虚构地区虚构作物软件阶段的积水记录包括观察日期与现场照片编号。",false,java.time.OffsetDateTime.parse("2026-11-10T10:00:00+08:00"))),
                input->{count.incrementAndGet();throw new IllegalStateException("模拟网络结果未知");},ControlledAgronomyAssistantWorkflowTest.official("虚构地区虚构作物软件阶段的积水记录包括观察日期与现场照片编号。",clock));
        assertEquals("UNKNOWN",coordinator(work).submit(authorization(101L,1L),id,command(key)).state());assertEquals(1,count.get());
        var steps=new ObjectMapper().readTree(stepJson(key));assertEquals("SUCCEEDED",steps.path("SEARCH").path("state").asText());assertEquals("UNKNOWN",steps.path("MODEL").path("state").asText());
        assertEquals("UNKNOWN",coordinator(work).submit(authorization(101L,1L),id,command(key)).state());assertEquals(1,count.get());
    }
    @Test void sourceReadsNeedSuccessfulSearchAndAreBoundedPerOriginalExecution() {
        long id=create(101);String key=UUID.randomUUID().toString();var reads=new AtomicInteger();
        assertEquals("STALE",coordinator((s,calls)->{
            calls.begin(AssistantCallLedger.Stage.SEARCH);calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
            for(int n=0;n<4;n++){calls.beforeSourceRead();assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());reads.incrementAndGet();}
            throw new AssertionError("最多三次读取");
        }).submit(authorization(101L,1L),id,command(key)).outcomeKind());assertEquals(3,reads.get());
        long other=create(101);String second=UUID.randomUUID().toString();
        assertEquals("STALE",coordinator((s,calls)->{calls.beforeSourceRead();throw new AssertionError("搜索前不可读取");}).submit(authorization(101L,1L),other,command(second)).outcomeKind());
    }
    @Test void changedFactsBeforeFreeSourceReadStopTheOriginalWorker() {
        long id=create(101);String key=UUID.randomUUID().toString();
        assertEquals("STALE",coordinator((s,calls)->{
            calls.begin(AssistantCallLedger.Stage.SEARCH);calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
            jdbc.update("UPDATE agronomy_case SET version=version+1,facts_version=facts_version+1 WHERE id=?",id);
            calls.beforeSourceRead();throw new AssertionError("不可读旧农情来源");
        }).submit(authorization(101L,1L),id,command(key)).outcomeKind());
    }
    @Test void forgedReferenceFlagsWithoutOfficialEvidenceAreNotStaged() {
        long id=create(101);String key=UUID.randomUUID().toString();
        var result=coordinator((s,calls)->"{\"kind\":\"REFERENCE\",\"publicationEligible\":true,\"verifiedDirectFetch\":true}").submit(authorization(101L,1L),id,command(key));
        assertEquals("UNKNOWN",result.state());assertFalse(requests.find(cases.findRequest("CUSTOMER",101L,key).orElseThrow().id()).orElseThrow().outcome().contains("publicationEligible"));
    }
    @Test void originalWechatAuthorityMustBeFrozenAndCheckedBeforeEachStageAndFinalResult() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var current=new java.util.concurrent.atomic.AtomicReference<>(authorization(101L,1L));
        var auth=mock(com.agri_system.application.auth.service.AuthService.class);
        when(auth.isCurrentClientAuthorization(any())).thenAnswer(a->a.getArgument(0).equals(current.get()));
        var sent=new AtomicInteger();var c=new AgronomyAssistantCoordinator(cases,requests,stores,customers,auth,manager,new ObjectMapper(),clock,policy,(s,usage)->{
            assertEquals(current.get(),s.authorization());usage.begin(AssistantCallLedger.Stage.SEARCH);sent.incrementAndGet();usage.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);
            current.set(new com.agri_system.domain.auth.model.ClientAuthorization(101,1,"WECHAT","wx1111111111111111",1101L,2L,UUID.randomUUID().toString()));
            usage.begin(AssistantCallLedger.Stage.MODEL);throw new AssertionError("身份关联变化后不得调用模型");
        });
        assertEquals("STALE",c.submit(current.get(),id,command(key)).outcomeKind());assertEquals(1,sent.get());
        assertFalse(stepJson(key).contains("MODEL"));assertEquals(11810,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
        var stored=jdbc.queryForObject("SELECT assistant_snapshot FROM agronomy_case_entry WHERE request_key=?",String.class,key);
        assertTrue(stored.contains("authorizationId"));assertFalse(stored.contains("tokenValue"));
    }
    @Test void forgedAuthorityOrLoggedOutOriginCannotReserveAndNewSessionCannotDispatchQueuedOriginal() {
        long id=create(101);String key=UUID.randomUUID().toString();var original=authorization(101L,1L);
        var current=new java.util.concurrent.atomic.AtomicReference<>(original);var auth=mock(com.agri_system.application.auth.service.AuthService.class);
        when(auth.isCurrentClientAuthorization(any())).thenAnswer(a->a.getArgument(0).equals(current.get()));
        var c=new AgronomyAssistantCoordinator(cases,requests,stores,customers,auth,manager,new ObjectMapper(),clock,policy,(s,calls)->{throw new AssertionError("不得接管旧许可");});
        var forged=new com.agri_system.domain.auth.model.ClientAuthorization(101,1,"WECHAT","wx1111111111111111",1101L,1L,UUID.randomUUID().toString());
        final var initial=c;assertThrows(IllegalArgumentException.class,()->initial.submit(forged,id,command(key)));assertEquals(1,cases.find(id,false).orElseThrow().version());
        requests=spy(requests);doThrow(new IllegalStateException("模拟未派进程停止")).when(requests).claim(anyLong(),anyString(),any());
        c=new AgronomyAssistantCoordinator(cases,requests,stores,customers,auth,manager,new ObjectMapper(),clock,policy,(s,calls)->{throw new AssertionError();});
        final var queued=c;assertThrows(IllegalStateException.class,()->queued.submit(original,id,command(key)));doCallRealMethod().when(requests).claim(anyLong(),anyString(),any());
        current.set(forged);assertEquals("FAILED",c.submit(forged,id,command(key)).state());
        assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
        current.set(null);assertThrows(IllegalArgumentException.class,()->queued.read(forged,id,key));
    }
    @Test void localProfessionalResultQueuesTheRealOriginalCaseExactlyOnceAndKeepsReceipt() {
        long id=create(101);jdbc.update("UPDATE agronomy_case SET status='COLLECTING' WHERE id=?",id);String key=UUID.randomUUID().toString();
        var calls=new AtomicInteger();var c=coordinator((s,usage)->{calls.incrementAndGet();return "{\"kind\":\"HUMAN\",\"published\":false,\"reason\":\"需要门店进一步核对\"}";});
        var first=c.submit(authorization(101L,1L),id,command(key));
        assertEquals("WAITING_REVIEW",cases.find(id,false).orElseThrow().status());assertEquals(3,cases.find(id,false).orElseThrow().version());
        assertEquals("2",first.queuedVersion());assertEquals(2,cases.findRequest("CUSTOMER",101L,key).orElseThrow().resultVersion());
        assertEquals(first,c.submit(authorization(101L,1L),id,command(key)));assertEquals(1,calls.get());
        assertEquals(3,cases.find(id,false).orElseThrow().version());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry WHERE case_id=?",Integer.class,id));
        assertEquals("WAITING_REVIEW",new ObjectMapper().valueToTree(first).path("handoffStatus").asText());
    }
    @Test void humanHandoffRollsBackWithOutcomeAndStaleFactsMustNotQueueOrPublish() {
        long id=create(101);jdbc.update("UPDATE agronomy_case SET status='COLLECTING' WHERE id=?",id);String key=UUID.randomUUID().toString();
        requests=spy(requests);doThrow(new IllegalStateException("模拟最终落库失败")).when(requests).finish(anyLong(),anyString(),anyString(),anyString(),any());
        var c=coordinator((s,usage)->"{\"kind\":\"HUMAN\",\"published\":false}");
        assertThrows(IllegalStateException.class,()->c.submit(authorization(101L,1L),id,command(key)));
        assertEquals("COLLECTING",cases.find(id,false).orElseThrow().status());assertEquals(2,cases.find(id,false).orElseThrow().version());
        assertNull(jdbc.queryForObject("SELECT assistant_outcome FROM agronomy_case_entry WHERE request_key=?",String.class,key));
        long other=create(101);jdbc.update("UPDATE agronomy_case SET status='COLLECTING' WHERE id=?",other);
        doCallRealMethod().when(requests).finish(anyLong(),anyString(),anyString(),anyString(),any());
        var stale=coordinator((s,usage)->{jdbc.update("UPDATE agronomy_case SET version=3,facts_version=2 WHERE id=?",other);return "{\"kind\":\"HUMAN\",\"published\":false}";});
        assertEquals("STALE",stale.submit(authorization(101L,1L),other,command(UUID.randomUUID().toString())).outcomeKind());
        assertEquals("COLLECTING",cases.find(other,false).orElseThrow().status());
    }
    @Test void originSessionRevocationStopsOfficialReadAndLateLocalResult() {
        for(boolean officialRead:new boolean[]{true,false}) {
            long id=create(101);String key=UUID.randomUUID().toString();var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
            var auth=mock(com.agri_system.application.auth.service.AuthService.class);when(auth.isCurrentClientAuthorization(any())).thenAnswer(a->allowed.get());
            var c=new AgronomyAssistantCoordinator(cases,requests,stores,customers,auth,manager,new ObjectMapper(),clock,policy,(s,calls)->{
                if(officialRead){calls.begin(AssistantCallLedger.Stage.SEARCH);calls.succeeded(AssistantCallLedger.Stage.SEARCH,0,0);}
                allowed.set(false);if(officialRead)calls.beforeSourceRead();
                return "{\"kind\":\"HUMAN\",\"published\":false,\"reason\":\"NOT_VISIBLE\"}";
            });
            assertEquals("STALE",c.submit(authorization(101L,1L),id,command(key)).outcomeKind());
            assertFalse(jdbc.queryForObject("SELECT assistant_outcome FROM agronomy_case_entry WHERE request_key=?",String.class,key).contains("NOT_VISIBLE"));
        }
    }
    @Test void legacyQueuedWithoutOriginCannotBorrowCurrentSessionOrDispatch() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();queued(id,key);var entry=cases.findRequest("CUSTOMER",101L,key).orElseThrow();
        var mapper=new ObjectMapper();var raw=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(requests.find(entry.id()).orElseThrow().snapshot());raw.remove("authorization");
        String saved=mapper.writeValueAsString(raw);String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(java.util.List.of("ASSISTANT_REQUEST",id,"1","1",true,saved))));
        jdbc.update("UPDATE agronomy_case_entry SET assistant_snapshot=?,request_hash=? WHERE id=?",saved,hash,entry.id());
        var c=coordinator((s,calls)->{throw new AssertionError("旧快照不能借当前身份执行");});
        assertEquals("QUEUED",c.read(authorization(101L,1L),id,key).state());assertEquals("FAILED",c.submit(authorization(101L,1L),id,command(key)).state());
        assertEquals(0,requests.usage(1L,java.time.LocalDate.of(2026,11,11)).reservedMicrousd());
    }
    @Test void customerAvailabilityChecksRealOwnerConfigurationAndQuotaWithoutReserving() {
        long id=create(101);policy=AssistantBudgetPolicy.disabled();var disabled=coordinator((s,calls)->{throw new AssertionError();});
        assertFalse(disabled.capability(authorization(101L,1L),id).enabled());
        assertThrows(RuntimeException.class,()->disabled.capability(authorization(202L,1L),id));
        assertEquals(1,cases.find(id,false).orElseThrow().version());
        policy=policy(true,1,1,11810);var c=coordinator((s,calls)->"{}");assertTrue(c.capability(authorization(101L,1L),id).enabled());
        c.submit(authorization(101L,1L),id,command(UUID.randomUUID().toString()));
        long other=create(101);assertEquals("QUOTA_EXHAUSTED",c.capability(authorization(101L,1L),other).reason());
    }
    @Test void customerProjectionDoesNotExposeInternalDraftAndKeepsUnknownDistinctFromMissing() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();var c=coordinator((s,calls)->{throw new IllegalStateException("PRIVATE");});
        var absent=c.customerResult(authorization(101L,1L),id,key);assertEquals("NOT_FOUND",absent.state());
        c.submit(authorization(101L,1L),id,command(key));var unknown=c.customerResult(authorization(101L,1L),id,key);
        assertEquals("UNKNOWN",unknown.state());assertTrue(unknown.citations().isEmpty());assertTrue(unknown.missingFacts().isEmpty());
        assertThrows(RuntimeException.class,()->c.customerResult(authorization(202L,1L),id,key));
        assertFalse(new ObjectMapper().writeValueAsString(unknown).contains("snapshot"));
    }
    @Test void completedLocalClarificationIsDerivedFromOriginalFactsNotArbitraryDraftText() throws Exception {
        long id=create(101);jdbc.update("UPDATE agronomy_case SET region='' WHERE id=?",id);
        var c=coordinator((s,calls)->"{\"kind\":\"CLARIFY\",\"published\":false,\"reason\":\"ARBITRARY_PRIVATE_TEXT\",\"missingFacts\":[\"unsafe-field\"]}");
        String key=UUID.randomUUID().toString();c.submit(authorization(101L,1L),id,command(key));var result=c.customerResult(authorization(101L,1L),id,key);
        assertEquals("CLARIFY",result.state());assertEquals(java.util.List.of("region"),result.missingFacts());
        assertFalse(new ObjectMapper().writeValueAsString(result).contains("ARBITRARY_PRIVATE_TEXT"));
        jdbc.update("UPDATE agronomy_case SET facts_version=2 WHERE id=?",id);
        assertEquals("STALE",c.customerResult(authorization(101L,1L),id,key).state());
    }
    @Test void completedReferenceOnlyExposesVerifiedQuotesAndExpiryRemovesThem() throws Exception {
        long id=create(101);String key=UUID.randomUUID().toString();String quote="虚构地区虚构作物软件阶段的积水记录包括观察日期与现场照片编号。";
        var work=new ControlledAgronomyAssistantWorkflow(new ObjectMapper(),clock,policy.execution(),q->java.util.List.of(
            new com.agri_system.domain.agronomy.assistant.AgronomySearchGateway.Candidate("软件验收资料","https://www.moa.gov.cn/test-fixture",quote,false,java.time.OffsetDateTime.parse("2026-11-10T10:00:00+08:00"))),
            input->new com.agri_system.domain.agronomy.assistant.AgronomyModelGateway.Output("deepseek-flash","{\"quotes\":[{\"sourceId\":\"S1\",\"text\":\""+quote+"\"}]}",100,100),ControlledAgronomyAssistantWorkflowTest.official(quote,clock));
        var c=coordinator(work);c.submit(authorization(101L,1L),id,command(key));var result=c.customerResult(authorization(101L,1L),id,key);
        assertEquals("REFERENCE",result.state());assertEquals(1,result.citations().size());assertEquals(quote,result.citations().get(0).quote());
        String raw=new ObjectMapper().writeValueAsString(result);for(String hidden:java.util.List.of("excerpt","Sha256","authorizationId","claimKey","Microusd","draft","policyVersion"))assertFalse(raw.contains(hidden));
        var later=new AgronomyAssistantCoordinator(cases,requests,stores,customers,clientAuth,manager,new ObjectMapper(),Clock.offset(clock,java.time.Duration.ofDays(2)),policy,(a,b)->{throw new AssertionError("查询不执行");});
        var expired=later.customerResult(authorization(101L,1L),id,key);assertEquals("UNAVAILABLE",expired.state());assertTrue(expired.citations().isEmpty());
    }
    static com.agri_system.domain.auth.model.ClientAuthorization authorization(Long customer,Long version) {
        return new com.agri_system.domain.auth.model.ClientAuthorization(customer,version,"WECHAT","wx1111111111111111",customer+1000,1L,String.format("00000000-0000-4000-8000-%012d",customer));
    }

}
