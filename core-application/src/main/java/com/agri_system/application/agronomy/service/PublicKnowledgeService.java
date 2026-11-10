package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.model.KnowledgeRevision;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository.Scope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicKnowledgeService {
    private final KnowledgeRevisionRepository rows;
    public record Summary(String id,String logicalKey,int revision,String title,String summary,Scope scope,String effectiveUntil,String reviewedAt) {}
    public record Page(List<Summary> rows,String nextAfter) {}
    public record Detail(Summary article,String body,String appliesTo,String exclusions,String sourceText,String effectiveFrom,String contentHash) {}
    public List<Scope> scopes(){var values=rows.publicScopes(KnowledgeRevisionService.today());if(values.size()>200)throw new IllegalArgumentException("适用范围过多，请联系门店维护知识分类");for(var value:values)scope(value.region(),value.crop(),value.growthStage());return List.copyOf(values);}
    public Page page(String region,String crop,String stage,Long after,String keyword){var scope=scope(region,crop,stage);if(after!=null&&after<=0)throw new IllegalArgumentException("知识分页编号无效");String search=keyword==null?null:KnowledgeRevisionService.plain(keyword,100,false,false).strip();var raw=rows.publicPage(scope,KnowledgeRevisionService.today(),after,search);var visible=raw.subList(0,Math.min(20,raw.size()));for(var value:visible)eligible(value,scope);return new Page(visible.stream().map(PublicKnowledgeService::summary).toList(),raw.size()>20?visible.get(19).id().toString():null);}
    public Detail detail(String key,int expectedRevision,String region,String crop,String stage){KnowledgeRevisionService.uuid(key);if(expectedRevision<=0)throw unavailable();var scope=scope(region,crop,stage);var r=rows.publicCurrent(key,scope,KnowledgeRevisionService.today()).orElseThrow(PublicKnowledgeService::unavailable);eligible(r,scope);if(r.revision()!=expectedRevision||!r.logicalKey().equals(key))throw unavailable();var c=r.content();return new Detail(summary(r),c.body(),c.appliesTo(),c.exclusions(),c.sourceText(),c.effectiveFrom().toString(),r.contentHash());}
    private static Scope scope(String region,String crop,String stage){return new Scope(KnowledgeRevisionService.plain(region,100,false,true).strip(),KnowledgeRevisionService.plain(crop,50,false,true).strip(),KnowledgeRevisionService.plain(stage,100,false,true).strip());}
    private static void eligible(KnowledgeRevision row,Scope scope){KnowledgeRevisionService.integrity(row);var c=row.content();var today=KnowledgeRevisionService.today();if(!row.state().equals("PUBLISHED")||!c.audience().equals("PUBLIC")||!c.riskLevel().equals("LOW")||row.reviewedBy()==null||row.reviewedBy()<=0||row.reviewedAt()==null||c.effectiveFrom()==null||c.effectiveUntil()==null||c.effectiveFrom().isAfter(today)||c.effectiveUntil().isBefore(today)||!c.region().equals(scope.region())||!c.crop().equals(scope.crop())||!c.growthStage().equals(scope.growthStage()))throw unavailable();}
    private static Summary summary(KnowledgeRevision r){var c=r.content();return new Summary(r.id().toString(),r.logicalKey(),r.revision(),c.title(),c.summary(),new Scope(c.region(),c.crop(),c.growthStage()),c.effectiveUntil().toString(),r.reviewedAt().toString());}
    private static IllegalArgumentException unavailable(){return new IllegalArgumentException("知识已撤回、更新、超过适用期限或不适用，请返回重新查询或联系门店");}
}
