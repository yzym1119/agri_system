package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.model.KnowledgeRevision;
import com.agri_system.domain.agronomy.model.KnowledgeRevision.Content;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository.Scope;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicKnowledgeServiceTest {
    KnowledgeRevisionRepository rows=mock(KnowledgeRevisionRepository.class);PublicKnowledgeService service=new PublicKnowledgeService(rows);
    Scope scope=new Scope("大庆","玉米","苗期");String key="11111111-1111-4111-8111-111111111111";
    KnowledgeRevision row(String state,String audience,String risk,Scope s){var today=KnowledgeRevisionService.today();var c=new Content("虚构知识","仅软件验收",s.crop(),s.region(),s.growthStage(),"适用范围","不适用范围","测试正文\n第二行","虚构来源",audience,risk,today,today.plusDays(1));return new KnowledgeRevision(1L,key,1,1L,c,KnowledgeRevisionService.contentHash(c),state,3,9L,today.atStartOfDay(),9L,today.atStartOfDay(),today.atStartOfDay(),null,key,"private-request-hash");}
    @Test void publicPageIsWhitelistedAndRequiresExplicitFullScope(){
        when(rows.publicPage(eq(scope),any(),isNull(),eq("%_"))).thenReturn(List.of(row("PUBLISHED","PUBLIC","LOW",scope)));
        var result=service.page("大庆","玉米","苗期",null,"%_");assertEquals(1,result.rows().size());assertEquals("大庆",result.rows().get(0).scope().region());
        assertFalse(result.toString().contains("private-request-hash"));
        assertThrows(IllegalArgumentException.class,()->service.page("大庆","玉米","",null,null));
        assertThrows(IllegalArgumentException.class,()->service.page("大庆","玉米","苗期",-1L,null));
    }
    @Test void publicServiceRejectsAnyRepositoryLeakAndWrongOriginalRevision(){
        for(var bad:List.of(row("DRAFT","PUBLIC","LOW",scope),row("PUBLISHED","INTERNAL","LOW",scope),row("PUBLISHED","PUBLIC","REQUIRES_HUMAN",scope),row("PUBLISHED","PUBLIC","LOW",new Scope("外地","玉米","苗期")))){
            when(rows.publicCurrent(eq(key),eq(scope),any())).thenReturn(Optional.of(bad));assertThrows(IllegalArgumentException.class,()->service.detail(key,1,"大庆","玉米","苗期"));
        }
        when(rows.publicCurrent(eq(key),eq(scope),any())).thenReturn(Optional.of(row("PUBLISHED","PUBLIC","LOW",scope)));
        assertThrows(IllegalArgumentException.class,()->service.detail(key,2,"大庆","玉米","苗期"));assertEquals("测试正文\n第二行",service.detail(key,1,"大庆","玉米","苗期").body());
    }
    @Test void scopesAreDerivedFromRealPublicRowsAndNeverSilentlyTruncated(){
        when(rows.publicScopes(any())).thenReturn(List.of(scope));assertEquals(List.of(scope),service.scopes());
        when(rows.publicScopes(any())).thenReturn(java.util.Collections.nCopies(201,scope));assertThrows(IllegalArgumentException.class,()->service.scopes());
    }
}
