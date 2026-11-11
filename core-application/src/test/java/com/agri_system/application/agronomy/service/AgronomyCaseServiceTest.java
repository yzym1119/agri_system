package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.model.SysOperationLog;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.infrastructure.agronomy.repository.AgronomyCaseRepositoryImpl;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真H2执行仓储SQL与事务，不以内存仓储替身证明追加/回滚。MySQL/Flex另验。 */
class AgronomyCaseServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    AgronomyCaseRepositoryImpl rows;
    StaffRepository staff=mock(StaffRepository.class);
    SysRoleRepository roles=mock(SysRoleRepository.class);
    StaffAuthorityService authority=mock(StaffAuthorityService.class);
    StorePublicationRepository stores=mock(StorePublicationRepository.class);
    SysOperationLogRepository logs=mock(SysOperationLogRepository.class);
    AgronomyCaseService service;
    String key="11111111-1111-4111-8111-111111111111";
    Facts facts=new Facts("大庆","玉米","不清楚");
    @BeforeEach void setup(){
        var ds=new DriverManagerDataSource("jdbc:h2:mem:case_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("CREATE TABLE agronomy_case(id BIGINT AUTO_INCREMENT PRIMARY KEY,customer_id BIGINT NOT NULL,store_id BIGINT NOT NULL,request_key VARCHAR(36) NOT NULL,request_hash VARCHAR(64) NOT NULL,question VARCHAR(2000) NOT NULL,region VARCHAR(100) NOT NULL,crop VARCHAR(50) NOT NULL,growth_stage VARCHAR(100) NOT NULL,status VARCHAR(24) NOT NULL,version BIGINT NOT NULL,facts_version BIGINT NOT NULL,assigned_staff_id BIGINT,created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,UNIQUE(customer_id,request_key))");
        jdbc.execute("CREATE TABLE agronomy_case_entry(id BIGINT AUTO_INCREMENT PRIMARY KEY,case_id BIGINT NOT NULL,actor_type VARCHAR(16) NOT NULL,actor_id BIGINT NOT NULL,request_key VARCHAR(36) NOT NULL,request_hash VARCHAR(64) NOT NULL,type VARCHAR(24) NOT NULL,body VARCHAR(4000) NOT NULL,source_text VARCHAR(2000) NOT NULL,region VARCHAR(100) NOT NULL,crop VARCHAR(50) NOT NULL,growth_stage VARCHAR(100) NOT NULL,assigned_staff_id BIGINT,expected_version BIGINT NOT NULL,result_version BIGINT NOT NULL,result_facts_version BIGINT NOT NULL,result_status VARCHAR(24) NOT NULL,created_at TIMESTAMP NOT NULL,UNIQUE(actor_type,actor_id,request_key))");
        rows=new AgronomyCaseRepositoryImpl(jdbc);service=new AgronomyCaseService(rows,staff,roles,authority,stores,logs);
        for(long id:List.of(9L,10L,11L)){var s=new Staff();s.setId(id);s.setStoreId(id==11?2L:1L);s.setStatus(1);s.setDeleted(0);when(staff.findIdentityById(id)).thenReturn(Optional.of(s));when(roles.findRoleCodesByStaffId(id)).thenReturn(List.of("SALE"));}
        when(authority.loadPermissions(9L)).thenReturn(List.of("agronomy:case:query","agronomy:case:manage","agronomy:case:review"));
        when(authority.loadPermissions(10L)).thenReturn(List.of("agronomy:case:query"));
        when(stores.find(eq(1L),anyBoolean())).thenReturn(Optional.of(new StorePublicationRepository.State(1L,"虚构门店",null,null,1,0,1,1L)));
    }
    AgronomyCaseService.Receipt create(){return tx.execute(s->service.create(101L,new AgronomyCaseService.Create(key,1L,"仅软件测试问题，不作农技建议",facts)));}
    AgronomyCaseService.Receipt change(long customer,String id,String action,String request,String version,String body,Facts f){return tx.execute(s->service.changeCustomer(customer,Long.valueOf(id),action,new AgronomyCaseService.Change(request,version,body,null,f,null)));}
    AgronomyCaseService.Receipt staffChange(String id,String action,String request,String version,String body,String source,Long target){return tx.execute(s->service.changeStaff(9L,Long.valueOf(id),action,new AgronomyCaseService.Change(request,version,body,source,null,target)));}
    @Test void originalCreationReplaysTenTimesAndAlteredBodyOrStoreCannotReuseItsRequest(){
        var first=create();for(int n=0;n<10;n++)assertEquals(first,create());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.create(101L,new AgronomyCaseService.Create(key,1L,"改问题",facts))));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.create(101L,new AgronomyCaseService.Create(key,2L,"仅软件测试问题，不作农技建议",facts))));
        assertEquals(first,service.submissionCustomer(101L,key).receipt());assertEquals("UNKNOWN",service.submissionCustomer(202L,key).status());
    }
    @Test void customerSupplementHumanHandoffAndActualReplyShareOneProblemAndImmutableReceipts(){
        var first=create();String more=UUID.randomUUID().toString(),human=UUID.randomUUID().toString(),reply=UUID.randomUUID().toString();
        var extra=change(101,first.caseId(),"SUPPLEMENT",more,"1","补充现场情况",new Facts("大庆","玉米","苗期"));assertEquals("2",extra.version());assertEquals("2",extra.factsVersion());
        var waiting=change(101,first.caseId(),"HUMAN_REQUEST",human,"2","请门店协助",null);assertEquals("WAITING_REVIEW",waiting.status());assertEquals("2",waiting.factsVersion());
        var answered=staffChange(first.caseId(),"HUMAN_REPLY",reply,"3","已收到，请补充现场照片，此为软件测试","虚构门店软件验收",null);assertEquals("ACTIVE",answered.status());
        assertEquals(extra,change(101,first.caseId(),"SUPPLEMENT",more,"1","补充现场情况",new Facts("大庆","玉米","苗期")),"后续变化不改原收据");
        var view=service.detailCustomer(101L,Long.valueOf(first.caseId()),null);assertEquals("4",view.problem().version());assertEquals("2",view.problem().factsVersion());assertEquals(4,view.entries().size());
        assertEquals(first,service.submissionCustomer(101L,key).receipt());assertThrows(IllegalArgumentException.class,()->change(101,first.caseId(),"HUMAN_REQUEST",more,"1","补充现场情况",null));
    }
    @Test void staleFactsConfirmationAndCrossCustomerCannotAppendOrReadPrivateProblem(){
        var first=create();change(101,first.caseId(),"SUPPLEMENT",UUID.randomUUID().toString(),"1","新情况",facts);
        assertThrows(IllegalArgumentException.class,()->staffChange(first.caseId(),"HUMAN_REPLY",UUID.randomUUID().toString(),"1","旧农情下的答复","测试来源",null));
        assertThrows(IllegalArgumentException.class,()->service.detailCustomer(202L,Long.valueOf(first.caseId()),null));
        assertThrows(IllegalArgumentException.class,()->change(202,first.caseId(),"HUMAN_REQUEST",UUID.randomUUID().toString(),"2","越权",null));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));assertTrue(service.pageCustomer(202L,null,null).rows().isEmpty());
    }
    @Test void assignedEmployeeSeesOnlyOwnCasesAndCannotBorrowManageOrReviewPermission(){
        var a=create();var b=tx.execute(s->service.create(202L,new AgronomyCaseService.Create(UUID.randomUUID().toString(),1L,"另一个问题",facts)));
        assertTrue(service.pageStaff(10L,null,null,null).rows().isEmpty());assertThrows(IllegalArgumentException.class,()->service.detailStaff(10L,Long.valueOf(a.caseId()),null));
        staffChange(a.caseId(),"ASSIGN",UUID.randomUUID().toString(),"1","",null,10L);
        assertEquals(1,service.pageStaff(10L,null,null,null).rows().size());assertEquals(a.caseId(),service.pageStaff(10L,null,null,null).rows().get(0).problem().id());
        assertThrows(IllegalArgumentException.class,()->service.detailStaff(10L,Long.valueOf(b.caseId()),null));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.changeStaff(10L,Long.valueOf(a.caseId()),"HUMAN_REPLY",new AgronomyCaseService.Change(UUID.randomUUID().toString(),"2","越权专业答复","测试来源",null,null))));
        assertThrows(IllegalArgumentException.class,()->staffChange(a.caseId(),"ASSIGN",UUID.randomUUID().toString(),"2","",null,11L));
        assertEquals(1,service.detailCustomer(101L,Long.valueOf(a.caseId()),null).entries().size(),"内部派给谁不向客户下发");
        when(authority.loadPermissions(9L)).thenReturn(List.of("agronomy:case:query"));assertThrows(IllegalArgumentException.class,()->service.detailStaff(9L,Long.valueOf(b.caseId()),null));
    }
    @Test void reassignmentPreservesOnlyOriginalActorsImmutableReceiptAndReplayWithoutReopeningProblemScope() {
        when(authority.loadPermissions(10L)).thenReturn(List.of("agronomy:case:query", "agronomy:case:review"));
        var first = create();
        staffChange(first.caseId(), "ASSIGN", UUID.randomUUID().toString(), "1", "交给员工", null, 10L);
        String replyKey = UUID.randomUUID().toString();
        var command = new AgronomyCaseService.Change(replyKey, "2", "仅软件核验答复", "软件依据", null, null);
        var original = tx.execute(s -> service.changeStaff(10L, Long.valueOf(first.caseId()), "HUMAN_REPLY", command));
        staffChange(first.caseId(), "ASSIGN", UUID.randomUUID().toString(), "3", "重新分配", null, 9L);
        assertThrows(IllegalArgumentException.class, () -> service.detailStaff(10L, Long.valueOf(first.caseId()), null));
        assertEquals(original, service.submissionStaff(10L, replyKey).receipt());
        assertEquals(original, tx.execute(s -> service.changeStaff(10L, Long.valueOf(first.caseId()), "HUMAN_REPLY", command)));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> service.changeStaff(10L, Long.valueOf(first.caseId()), "HUMAN_REPLY",
                new AgronomyCaseService.Change(replyKey, "2", "篡改内容", "软件依据", null, null))));
        assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> service.changeStaff(10L, Long.valueOf(first.caseId()), "HUMAN_REPLY",
                new AgronomyCaseService.Change(UUID.randomUUID().toString(), "4", "越权新回复", "软件依据", null, null))));
        assertEquals("UNKNOWN", service.submissionStaff(9L, replyKey).status());
        when(authority.loadPermissions(10L)).thenReturn(List.of("agronomy:case:query"));
        assertThrows(IllegalArgumentException.class, () -> service.submissionStaff(10L, replyKey));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry", Integer.class));
    }
    @Test void privateOrStoppedStoreRejectsNewQuestionButWithdrawingPublicationKeepsOwnedHistory(){
        var a=create();when(stores.find(eq(1L),anyBoolean())).thenReturn(Optional.of(new StorePublicationRepository.State(1L,"门店",null,null,1,0,0,2L)));
        assertNotNull(service.detailCustomer(101L,Long.valueOf(a.caseId()),null));assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.create(101L,new AgronomyCaseService.Create(UUID.randomUUID().toString(),1L,"新问题",facts))));
        change(101,a.caseId(),"HUMAN_REQUEST",UUID.randomUUID().toString(),"1","继续处理原问题",null);
        when(stores.find(eq(1L),anyBoolean())).thenReturn(Optional.of(new StorePublicationRepository.State(1L,"门店",null,null,0,0,0,3L)));
        assertThrows(IllegalArgumentException.class,()->change(101,a.caseId(),"SUPPLEMENT",UUID.randomUUID().toString(),"2","停用后新增",facts));assertNotNull(service.detailCustomer(101L,Long.valueOf(a.caseId()),null));
    }
    @Test void auditFailureRollsBackStaffReplyAndOriginalCaseVersionTogether(){
        var a=create();doThrow(new IllegalStateException("测试审计不可用")).when(logs).save(any());
        assertThrows(IllegalStateException.class,()->staffChange(a.caseId(),"HUMAN_REPLY",UUID.randomUUID().toString(),"1","不能留下的答复","测试来源",null));
        assertEquals("1",service.detailCustomer(101L,Long.valueOf(a.caseId()),null).problem().version());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
    }
    @Test void originalRequestIsGlobalWithinActorAndCannotMoveToAnotherProblemOrAction(){
        var a=create();var b=tx.execute(s->service.create(101L,new AgronomyCaseService.Create(UUID.randomUUID().toString(),1L,"同人的另一个问题",facts)));
        String req=UUID.randomUUID().toString();change(101,a.caseId(),"HUMAN_REQUEST",req,"1","请协助",null);
        assertThrows(IllegalArgumentException.class,()->change(101,b.caseId(),"HUMAN_REQUEST",req,"1","请协助",null));
        assertThrows(IllegalArgumentException.class,()->change(101,b.caseId(),"SUPPLEMENT",key,"1","复用创建号",facts));assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM agronomy_case_entry",Integer.class));
    }
    @Test void currentIdentityRequiredAndDiagnosticsNeverContainQuestionOrReply(){
        var c=new AgronomyCaseService.Create(key,1L,"PRIVATE_TEST_QUESTION",facts);var d=new AgronomyCaseService.Change(key,"1","PRIVATE_TEST_REPLY","PRIVATE_SOURCE",facts,null);
        assertFalse(c.toString().contains("PRIVATE_TEST_QUESTION"));assertFalse(d.toString().contains("PRIVATE_TEST_REPLY"));assertFalse(d.toString().contains("PRIVATE_SOURCE"));
        var a=create();when(staff.findIdentityById(9L)).thenReturn(Optional.empty());assertThrows(IllegalArgumentException.class,()->service.pageStaff(9L,null,null,null));
        assertThrows(IllegalArgumentException.class,()->service.pageCustomer(null,null,null));assertThrows(IllegalArgumentException.class,()->service.pageCustomer(101L,null,"GUESSED"));
    }
    @Test void bodyObjectIdsCannotBeTruncatedFromDecimalOrExponentNumbers() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertEquals(10L,json.readValue("{\"assignedStaffId\":10}",AgronomyCaseService.Change.class).assignedStaffId());
        assertEquals(10L,json.readValue("{\"assignedStaffId\":\"10\"}",AgronomyCaseService.Change.class).assignedStaffId());
        for(String invalid:List.of("10.9","10.0","1e1","\"010\"","\"10.9\"","-1","9223372036854775808")) {
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->json.readValue("{\"assignedStaffId\":"+invalid+"}",AgronomyCaseService.Change.class));
            assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->json.readValue("{\"storeId\":"+invalid+"}",AgronomyCaseService.Create.class));
        }
    }
    @Test void explicitInitialHumanRequestWaitsInOneCommitAndCannotChangeTheOriginalIntent() {
        var command=new AgronomyCaseService.Create(key,1L,"请门店协助，纯软件测试",facts,true);
        var first=tx.execute(s->service.create(101L,command));assertEquals("WAITING_REVIEW",first.status());assertEquals("1",first.version());
        assertEquals(1,service.detailCustomer(101L,Long.valueOf(first.caseId()),null).entries().size());
        assertEquals(first,tx.execute(s->service.create(101L,command)));
        assertThrows(IllegalArgumentException.class,()->tx.execute(s->service.create(101L,new AgronomyCaseService.Create(key,1L,"请门店协助，纯软件测试",facts,false))));
    }
    @Test void missingOrFalseHumanFlagPreservesAlreadySavedLegacyCreationFingerprint() {
        // 摘要取自上一已提交版本的真实虚构问题，不能用新实现生成期望值。
        String oldHash="8bd7c7615335bac954c37c7d60665e8f62b61deff36ff293cf3e6115c2f90575";
        var f=new Facts("虚构地区","虚构作物","");String question="虚构软件联动问题，不是农技咨询";
        var original=tx.execute(s->service.create(101L,new AgronomyCaseService.Create(key,1L,question,f)));
        jdbc.update("UPDATE agronomy_case SET request_hash=? WHERE id=?",oldHash,Long.valueOf(original.caseId()));
        jdbc.update("UPDATE agronomy_case_entry SET request_hash=? WHERE case_id=?",oldHash,Long.valueOf(original.caseId()));
        assertEquals(original,tx.execute(s->service.create(101L,new AgronomyCaseService.Create(key,1L,question,f,false))));
        assertEquals(original,tx.execute(s->service.create(101L,new AgronomyCaseService.Create(key,1L,question,f,null))));
    }
    @Test void explicitHumanIntentDoesNotCoerceTextOrIntegerIntoCustomerConsent() throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        assertTrue(json.readValue("{\"requestHuman\":true}",AgronomyCaseService.Create.class).requestHuman());
        assertFalse(json.readValue("{\"requestHuman\":false}",AgronomyCaseService.Create.class).requestHuman());
        for(String raw:List.of("1","0","\"true\"","\"false\"")) assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,()->json.readValue("{\"requestHuman\":"+raw+"}",AgronomyCaseService.Create.class));
    }
    @Test void latestRealReplyRemainsAvailableBeyondFirstHistoryPageAndRetainsItsOriginalFactsVersion() {
        var a=create();staffChange(a.caseId(),"HUMAN_REPLY",UUID.randomUUID().toString(),"1","虚构上次答复","软件测试依据",null);
        for(int v=2;v<=26;v++)change(101,a.caseId(),"SUPPLEMENT",UUID.randomUUID().toString(),Integer.toString(v),"补充虚构情况",facts);
        var detail=service.detailCustomer(101L,Long.valueOf(a.caseId()),null);
        assertEquals(20,detail.entries().size());assertTrue(detail.entries().stream().noneMatch(e->e.type().equals("HUMAN_REPLY")));
        assertEquals("虚构上次答复",detail.latestReply().body());assertEquals("1",detail.latestReply().factsVersion());assertEquals("26",detail.problem().factsVersion());
        assertTrue(rows.latestHumanReply(Long.valueOf(a.caseId()),1).isEmpty());
    }
    @Test void customerHistoryUsesPositiveVisibilityNotOnlyExcludingAssignment() {
        var a=create();
        for(var pair:List.of(List.of("STAFF","INTERNAL_NOTE"),List.of("STAFF","QUESTION"),List.of("CUSTOMER","HUMAN_REPLY"))) {
            jdbc.update("INSERT INTO agronomy_case_entry(case_id,actor_type,actor_id,request_key,request_hash,type,body,source_text,region,crop,growth_stage,assigned_staff_id,expected_version,result_version,result_facts_version,result_status,created_at) SELECT case_id,?,9,?,request_hash,?,'PRIVATE_TEST_NOTE','',region,crop,growth_stage,NULL,expected_version,result_version,result_facts_version,result_status,created_at FROM agronomy_case_entry WHERE id=?",pair.get(0),UUID.randomUUID().toString(),pair.get(1),Long.valueOf(a.entryId()));
        }
        var detail=service.detailCustomer(101L,Long.valueOf(a.caseId()),null);
        assertEquals(1,detail.entries().size());assertNull(detail.latestReply());assertEquals("QUESTION",detail.entries().get(0).type());
    }
}
