package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.model.KnowledgeRevision;
import com.agri_system.domain.agronomy.model.KnowledgeRevision.Content;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeRevisionServiceTest {
    KnowledgeRevisionRepository rows=mock(KnowledgeRevisionRepository.class);
    StaffRepository staff=mock(StaffRepository.class);SysRoleRepository roles=mock(SysRoleRepository.class);
    StorePublicationRepository stores=mock(StorePublicationRepository.class);SysOperationLogRepository logs=mock(SysOperationLogRepository.class);
    KnowledgeRevisionService service=new KnowledgeRevisionService(rows,staff,roles,stores,logs);
    String key="11111111-1111-4111-8111-111111111111",next="22222222-2222-4222-8222-222222222222";
    List<KnowledgeRevision> data=new ArrayList<>();
    @BeforeEach void setup(){
        var actor=new Staff();actor.setId(9L);actor.setStoreId(1L);actor.setStatus(1);actor.setDeleted(0);
        when(staff.findIdentityById(9L)).thenReturn(Optional.of(actor));when(roles.findRoleCodesByStaffId(9L)).thenReturn(List.of("SALE"));
        when(stores.find(eq(1L),anyBoolean())).thenReturn(Optional.of(new StorePublicationRepository.State(1L,"门店",null,null,1,0,0,1L)));
        when(rows.find(anyString(),anyInt(),anyBoolean())).thenAnswer(i->data.stream().filter(r->r.logicalKey().equals(i.getArgument(0))&&r.revision()==(int)i.getArgument(1)).findFirst());
        when(rows.findSubmission(anyLong(),anyString(),anyBoolean())).thenAnswer(i->data.stream().filter(r->r.createdBy().equals(i.getArgument(0))&&r.requestKey().equals(i.getArgument(1))).findFirst());
        when(rows.latestRevision(anyString())).thenAnswer(i->data.stream().filter(r->r.logicalKey().equals(i.getArgument(0))).mapToInt(KnowledgeRevision::revision).max().orElse(0));
        when(rows.latestPublishedRevision(anyString())).thenAnswer(i->data.stream().filter(r->r.logicalKey().equals(i.getArgument(0))&&r.publishedAt()!=null).mapToInt(KnowledgeRevision::revision).max().orElse(0));
        when(rows.insertIfAbsent(any())).thenAnswer(i->{var r=(KnowledgeRevision)i.getArgument(0);if(data.stream().anyMatch(x->x.logicalKey().equals(r.logicalKey())&&x.revision()==r.revision()||x.createdBy().equals(r.createdBy())&&x.requestKey().equals(r.requestKey())))return false;data.add(copy(r,(long)data.size()+1,r.state(),r.version(),null,null,null));return true;});
        when(rows.published(anyString())).thenAnswer(i->data.stream().filter(r->r.logicalKey().equals(i.getArgument(0))&&r.state().equals("PUBLISHED")).toList());
        when(rows.changeState(anyLong(),anyLong(),anyString(),anyLong(),any())).thenAnswer(i->{for(int n=0;n<data.size();n++){var r=data.get(n);if(r.id().equals(i.getArgument(0))&&r.version()==(long)i.getArgument(1)){String state=i.getArgument(2);data.set(n,copy(r,r.id(),state,r.version()+1,state.equals("REVIEWED")?9L:r.reviewedBy(),state.equals("REVIEWED")?i.getArgument(4):r.reviewedAt(),state.equals("PUBLISHED")?i.getArgument(4):r.publishedAt()));return true;}}return false;});
    }
    KnowledgeRevision copy(KnowledgeRevision r,Long id,String state,long version,Long reviewer,java.time.LocalDateTime reviewed,java.time.LocalDateTime published){return new KnowledgeRevision(id,r.logicalKey(),r.revision(),r.storeId(),r.content(),r.contentHash(),state,version,r.createdBy(),r.createdAt(),reviewer,reviewed,published,null,r.requestKey(),r.requestHash());}
    Content content(String body,String audience,String risk){LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));return new Content("虚构流程知识","仅测试流程","玉米","大庆","苗期","明确适用范围","明确不适用范围",body,"虚构来源，不作农技指导",audience,risk,today,today.plusDays(30));}
    KnowledgeRevisionService.Draft draft(String request,int latest,Content c){return new KnowledgeRevisionService.Draft(request,1L,latest,c);}
    @Test void fixedCreationRequestReplaysWithoutNewVersionButChangedBodyIsRejected(){
        var c=content("纯软件测试正文","PUBLIC","LOW");var first=service.create(9L,null,draft(key,0,c));assertEquals(1,first.revision());
        assertEquals(first.id(),service.create(9L,null,draft(key,0,c)).id());assertEquals(1,data.size());
        assertThrows(IllegalArgumentException.class,()->service.create(9L,null,draft(key,0,content("修改正文","PUBLIC","LOW"))));
        assertEquals("CREATED",service.submission(9L,key).status());assertEquals("UNKNOWN",service.submission(9L,next).status());
    }
    @Test void reviewAndPublishRequireCompleteFixedContentAndOriginalVersion(){
        service.create(9L,null,draft(key,0,content("","PUBLIC","LOW")));
        assertThrows(IllegalArgumentException.class,()->service.change(9L,key,1,"1","REVIEW","已核对"));
        assertThrows(IllegalArgumentException.class,()->service.change(9L,key,1,"1","PUBLISH","已核对"));
        service.create(9L,key,draft(next,1,content("测试正文","PUBLIC","LOW")));
        var reviewed=service.change(9L,key,2,"1","REVIEW","已核对来源和范围");assertEquals("REVIEWED",reviewed.state());
        assertThrows(IllegalArgumentException.class,()->service.change(9L,key,2,"1","PUBLISH","旧确认"));
        assertEquals("PUBLISHED",service.change(9L,key,2,"2","PUBLISH","确认公开").state());
    }
    @Test void newPublishedVersionWithdrawsOldInTheSameBusinessPathAndCannotReviveOld(){
        service.create(9L,null,draft(key,0,content("原正文","PUBLIC","LOW")));service.change(9L,key,1,"1","REVIEW","核对");service.change(9L,key,1,"2","PUBLISH","公开");
        service.create(9L,key,draft(next,1,content("新正文","PUBLIC","LOW")));service.change(9L,key,2,"1","REVIEW","核对");service.change(9L,key,2,"2","PUBLISH","替代");
        assertEquals("WITHDRAWN",data.get(0).state());assertEquals("原正文",data.get(0).content().body());assertEquals(1,data.stream().filter(r->r.state().equals("PUBLISHED")).count());
        assertThrows(IllegalArgumentException.class,()->service.change(9L,key,1,"4","PUBLISH","复活"));
    }
    @Test void replayPrecedesLatestVersionCheckAndStaleNewDraftDoesNotWrite(){
        var c=content("测试正文","INTERNAL","REQUIRES_HUMAN");service.create(9L,null,draft(key,0,c));service.create(9L,key,draft(next,1,c));
        assertEquals(2,service.create(9L,key,draft(next,1,c)).revision());
        assertThrows(IllegalArgumentException.class,()->service.create(9L,key,draft("33333333-3333-4333-8333-333333333333",1,c)));assertEquals(2,data.size());
    }
    @Test void publicHumanOnlyContentCannotPublishButInternalVersionCan(){
        service.create(9L,null,draft(key,0,content("测试正文","PUBLIC","REQUIRES_HUMAN")));service.change(9L,key,1,"1","REVIEW","人工核对");
        assertThrows(IllegalArgumentException.class,()->service.change(9L,key,1,"2","PUBLISH","公开"));
        service.create(9L,key,draft(next,1,content("测试正文","INTERNAL","REQUIRES_HUMAN")));service.change(9L,key,2,"1","REVIEW","人工核对");assertEquals("PUBLISHED",service.change(9L,key,2,"2","PUBLISH","内部学习").state());
    }
    @Test void actualActorStoreAndReasonAreRequiredAndUuidMustBeCanonical(){
        var foreign=new KnowledgeRevisionService.Draft(key,2L,0,content("正文","PUBLIC","LOW"));assertThrows(IllegalArgumentException.class,()->service.create(9L,null,foreign));
        assertThrows(IllegalArgumentException.class,()->service.create(9L,null,draft("bad",0,content("正文","PUBLIC","LOW"))));
        service.create(9L,null,draft(key,0,content("正文","PUBLIC","LOW")));assertThrows(IllegalArgumentException.class,()->service.change(9L,key,1,"1","REVIEW"," "));
        when(staff.findIdentityById(9L)).thenReturn(Optional.empty());assertThrows(IllegalArgumentException.class,()->service.detail(9L,key,1));
    }
    @Test void concurrentFirstRequestThatFindsOriginalAfterInsertDoesNotAuditTwice(){
        var c=content("测试正文","PUBLIC","LOW");service.create(9L,null,draft(key,0,c));
        when(rows.findSubmission(9L,key,true)).thenReturn(Optional.empty());
        assertEquals("1",service.create(9L,null,draft(key,0,c)).id());assertEquals(1,data.size());verify(logs,times(1)).save(any());
    }
    @Test void delayedOlderReviewedVersionCannotReplaceNewerPublishedContent(){
        service.create(9L,null,draft(key,0,content("旧审核稿","PUBLIC","LOW")));service.change(9L,key,1,"1","REVIEW","核对");
        service.create(9L,key,draft(next,1,content("新审核稿","PUBLIC","LOW")));service.change(9L,key,2,"1","REVIEW","核对");service.change(9L,key,2,"2","PUBLISH","发布新版");
        assertThrows(IllegalArgumentException.class,()->service.change(9L,key,1,"2","PUBLISH","迟到旧操作"));assertEquals("PUBLISHED",data.get(1).state());
    }
    @Test void draftDiagnosticsCannotCopyInternalKnowledgeIntoGenericHttpLogs(){
        var command=draft(key,0,content("INTERNAL_BODY_SENTINEL","INTERNAL","REQUIRES_HUMAN"));
        assertFalse(com.agri_system.domain.common.log.LogMaskingUtils.mask(command).contains("INTERNAL_BODY_SENTINEL"));
    }
}
