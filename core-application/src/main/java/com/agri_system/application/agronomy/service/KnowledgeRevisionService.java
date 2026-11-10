package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.model.KnowledgeRevision;
import com.agri_system.domain.agronomy.model.KnowledgeRevision.Content;
import com.agri_system.domain.agronomy.repository.KnowledgeRevisionRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.model.SysOperationLog;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KnowledgeRevisionService {
    private final KnowledgeRevisionRepository rows;
    private final StaffRepository staff;
    private final SysRoleRepository roles;
    private final StorePublicationRepository stores;
    private final SysOperationLogRepository logs;
    public record Draft(String requestKey,Long storeId,Integer expectedLatestRevision,Content content) {}
    public record Detail(String id,String logicalKey,int revision,String storeId,Content content,String contentHash,
            String state,String effectiveState,String version,String reviewedAt) {}
    public record Page(List<Detail> rows,String nextAfter) {}
    public record Submission(String requestKey,String status,Detail revision) {}
    private record Actor(Staff staff,boolean admin) {}

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Detail create(Long actorId,String logicalKey,Draft command){
        var actor=actor(actorId);
        if(command==null||command.storeId()==null||command.storeId()<=0||command.expectedLatestRevision()==null||command.expectedLatestRevision()<0)throw invalid("草稿门店和原最新版本必填");
        scope(actor,command.storeId());uuid(command.requestKey());String key=logicalKey==null?command.requestKey():uuid(logicalKey);
        if(logicalKey==null&&command.expectedLatestRevision()!=0)throw stale();
        Content content=normalized(command.content());String hash=contentHash(content);
        String requestHash=digest(key,command.storeId(),command.expectedLatestRevision(),hash);
        var existing=rows.findSubmission(actorId,command.requestKey(),true);
        if(existing.isPresent())return replay(actor,existing.get(),key,command.storeId(),requestHash);
        var now=now();
        if(logicalKey==null){
            boolean inserted=rows.insertIfAbsent(new KnowledgeRevision(null,key,1,command.storeId(),content,hash,"DRAFT",1,actorId,now,null,null,null,null,command.requestKey(),requestHash));
            var first=rows.find(key,1,true).orElseThrow(()->invalid("请求号已用于其他知识，请核对原提交"));
            var result=replay(actor,first,key,command.storeId(),requestHash);store(command.storeId(),true,true);
            // 只有本次首次插入才追加创建审计；重放在上方原请求分支返回。
            if(!Objects.equals(first.createdBy(),actorId))throw invalid("请求号已被使用");
            if(inserted)audit(actorId,first,"保存知识草稿","首版草稿");return result;
        }
        var anchor=rows.find(key,1,true).orElseThrow(()->invalid("知识不存在或无权查看"));scope(actor,anchor.storeId());
        if(!command.storeId().equals(anchor.storeId()))throw invalid("不能更改知识所属门店");
        existing=rows.findSubmission(actorId,command.requestKey(),true);
        if(existing.isPresent())return replay(actor,existing.get(),key,command.storeId(),requestHash);
        int latest=rows.latestRevision(key);if(latest!=command.expectedLatestRevision()||latest==Integer.MAX_VALUE)throw stale();
        store(anchor.storeId(),true,true);
        if(!rows.insertIfAbsent(new KnowledgeRevision(null,key,latest+1,anchor.storeId(),content,hash,"DRAFT",1,actorId,now,null,null,null,null,command.requestKey(),requestHash)))throw invalid("请求号已被使用，请核对原提交");
        var saved=rows.findSubmission(actorId,command.requestKey(),false).orElseThrow(()->invalid("草稿结果未能核对"));
        audit(actorId,saved,"保存知识草稿","追加新版本");return detail(saved);
    }

    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Detail change(Long actorId,String key,int revision,String expectedVersion,String action,String reason){
        var actor=actor(actorId);uuid(key);positive(revision);plain(reason,500,true,true);
        if(!Set.of("REVIEW","PUBLISH","WITHDRAW").contains(action==null?"":action))throw invalid("知识操作无效");
        var anchor=rows.find(key,1,true).orElseThrow(()->invalid("知识不存在或无权查看"));scope(actor,anchor.storeId());
        var row=rows.find(key,revision,true).orElseThrow(()->invalid("知识版本不存在"));scope(actor,row.storeId());
        long expected;try{expected=Long.parseLong(expectedVersion);}catch(RuntimeException bad){throw stale();}
        if(expected<=0||expected!=row.version())throw stale();integrity(row);
        if("WITHDRAWN".equals(row.state()))throw invalid("已撤回版本不能再次操作，请另存新版本");
        store(row.storeId(),!action.equals("WITHDRAW"),true);
        String target;
        if(action.equals("REVIEW")){
            if(!row.state().equals("DRAFT"))throw invalid("只有草稿可以审核");complete(row.content());target="REVIEWED";
        }else if(action.equals("PUBLISH")){
            if(!row.state().equals("REVIEWED")||row.reviewedBy()==null||row.reviewedBy()<=0||row.reviewedAt()==null)throw invalid("请先审核固定版本");
            if(row.revision()<=rows.latestPublishedRevision(key))throw invalid("已有更新的发布历史，请另存新版本，不能发布迟到旧稿");
            complete(row.content());if(row.content().effectiveUntil().isBefore(today()))throw invalid("知识审核适用期限已过，请另存新版核对");
            if(row.content().audience().equals("PUBLIC")&&!row.content().riskLevel().equals("LOW"))throw invalid("需人工处理的知识不能直接公开");
            var old=rows.published(key);if(old.size()>1)throw invalid("知识存在多个发布版本，请人工核对");
            for(var previous:old){integrity(previous);update(previous,"WITHDRAWN",actorId);audit(actorId,previous,"替换撤回旧知识","发布版本"+revision+"，替代旧版本");}
            target="PUBLISHED";
        }else target="WITHDRAWN";
        update(row,target,actorId);audit(actorId,row,action.equals("REVIEW")?"审核知识":action.equals("PUBLISH")?"发布知识":"撤回知识",reason);
        return detail(rows.find(key,revision,false).orElseThrow(()->invalid("知识状态未能核对")));
    }
    public Detail detail(Long actorId,String key,int revision){var actor=actor(actorId);uuid(key);positive(revision);var row=rows.find(key,revision,false).orElseThrow(()->invalid("知识版本不存在"));scope(actor,row.storeId());integrity(row);return detail(row);}
    public Submission submission(Long actorId,String requestKey){var actor=actor(actorId);uuid(requestKey);var row=rows.findSubmission(actorId,requestKey,false);if(row.isEmpty())return new Submission(requestKey,"UNKNOWN",null);scope(actor,row.get().storeId());integrity(row.get());return new Submission(requestKey,"CREATED",detail(row.get()));}
    public Page page(Long actorId,Long storeId,Long after){var actor=actor(actorId);if(after!=null&&after<=0)throw invalid("分页编号无效");if(storeId!=null)scope(actor,storeId);Long actual=actor.admin()?storeId:actor.staff().getStoreId();var raw=rows.page(actual,after);var page=raw.subList(0,Math.min(20,raw.size()));for(var r:page){scope(actor,r.storeId());integrity(r);}return new Page(page.stream().map(KnowledgeRevisionService::detail).toList(),raw.size()>20?page.get(19).id().toString():null);}
    private Detail replay(Actor actor,KnowledgeRevision row,String key,Long storeId,String requestHash){scope(actor,row.storeId());if(!row.createdBy().equals(actor.staff().getId())||!row.logicalKey().equals(key)||!row.storeId().equals(storeId)||!row.requestHash().equals(requestHash))throw invalid("同一提交号的内容或归属已变化，请核对原提交");integrity(row);return detail(row);}
    private void update(KnowledgeRevision row,String target,Long actor){if(row.version()<=0||row.version()==Long.MAX_VALUE)throw invalid("知识版本待人工核对");if(!rows.changeState(row.id(),row.version(),target,actor,now()))throw stale();}
    private Actor actor(Long id){if(id==null||id<=0)throw invalid("员工登录已失效");var value=staff.findIdentityById(id).orElseThrow(()->invalid("员工登录已失效"));if(!id.equals(value.getId())||!value.isActive()||!Integer.valueOf(0).equals(value.getDeleted()))throw invalid("员工登录已失效");var codes=roles.findRoleCodesByStaffId(id);boolean admin=codes.contains("ADMIN")||codes.contains("SUPER_ADMIN");if(!admin&&(value.getStoreId()==null||value.getStoreId()<=0))throw invalid("员工未分配门店");return new Actor(value,admin);}
    private static void scope(Actor actor,Long store){if(store==null||store<=0||!actor.admin()&&!store.equals(actor.staff().getStoreId()))throw invalid("只能处理本人门店的知识");}
    private void store(Long id,boolean active,boolean lock){var s=stores.find(id,lock).orElseThrow(()->invalid("门店不存在"));if(!id.equals(s.id())||!Integer.valueOf(0).equals(s.deleted())||active&&!Integer.valueOf(1).equals(s.status()))throw invalid("门店已停用或删除");}
    private void audit(Long actor,KnowledgeRevision row,String action,String reason){var log=new SysOperationLog();log.setStaffId(actor);log.setModule("农技知识");log.setAction(action);log.setStatus(1);log.setMethod("POST");log.setCreatedAt(now());log.setRequestUri("/api/admin/agronomy/knowledge/"+row.logicalKey()+"/revisions/"+row.revision());log.setRequestParams("知识="+row.logicalKey()+";版本="+row.revision()+";原状态版本="+row.version()+";正文摘要="+row.contentHash()+";原因="+reason);logs.save(log);}
    private static Detail detail(KnowledgeRevision r){return new Detail(r.id().toString(),r.logicalKey(),r.revision(),r.storeId().toString(),r.content(),r.contentHash(),r.state(),r.state().equals("PUBLISHED")&&r.content().effectiveUntil()!=null&&r.content().effectiveUntil().isBefore(today())?"EXPIRED":r.state(),Long.toString(r.version()),r.reviewedAt()==null?null:r.reviewedAt().toString());}
    public static LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));}
    public static String uuid(String value){try{if(value==null||!UUID.fromString(value).toString().equals(value)||value.charAt(14)!='4'||"89ab".indexOf(value.charAt(19))<0)throw invalid("知识与请求编号无效");return value;}catch(RuntimeException bad){throw invalid("知识与请求编号无效");}}
    private static void positive(int revision){if(revision<=0)throw invalid("知识版本无效");}
    public static void integrity(KnowledgeRevision row){if(row.id()==null||row.id()<=0||row.storeId()==null||row.storeId()<=0||row.revision()<=0||row.version()<=0||!contentHash(row.content()).equals(row.contentHash()))throw invalid("知识版本资料待核对");}
    private static Content normalized(Content c){if(c==null)throw invalid("知识内容必填");String audience=c.audience()==null?"INTERNAL":c.audience(),risk=c.riskLevel()==null?"REQUIRES_HUMAN":c.riskLevel();if(!Set.of("PUBLIC","INTERNAL").contains(audience)||!Set.of("LOW","REQUIRES_HUMAN").contains(risk))throw invalid("知识受众或风险范围无效");
        return new Content(plain(c.title(),200,false,true).strip(),plain(c.summary(),500,true,false),plain(c.crop(),50,false,false).strip(),plain(c.region(),100,false,false).strip(),plain(c.growthStage(),100,false,false).strip(),plain(c.appliesTo(),2000,true,false),plain(c.exclusions(),2000,true,false),plain(c.body(),20000,true,false),plain(c.sourceText(),2000,true,false),audience,risk,c.effectiveFrom(),c.effectiveUntil());}
    private static void complete(Content c){normalized(c);if(List.of(c.summary(),c.crop(),c.region(),c.growthStage(),c.appliesTo(),c.exclusions(),c.body(),c.sourceText()).stream().anyMatch(String::isBlank)||c.effectiveFrom()==null||c.effectiveUntil()==null||c.effectiveFrom().isAfter(c.effectiveUntil()))throw invalid("审核前请完善正文、摘要、适用/不适用范围、来源与有效期限");}
    public static String plain(String value,int max,boolean multi,boolean required){String v=value==null?"":value;if(v.length()>max||required&&v.isBlank()||(multi?v.replace("\n","").replace("\r",""):v).chars().anyMatch(c->c<32||c==127))throw invalid("知识字段为空、过长或含无效字符");return v;}
    public static String contentHash(Content c){if(c==null)throw invalid("知识内容待核对");return digest(c.title(),c.summary(),c.crop(),c.region(),c.growthStage(),c.appliesTo(),c.exclusions(),c.body(),c.sourceText(),c.audience(),c.riskLevel(),c.effectiveFrom(),c.effectiveUntil());}
    private static String digest(Object... values){var input=new StringBuilder();for(Object v:values){if(v==null)input.append('N');else{String s=v.toString();input.append('S').append(s.length()).append(':').append(s);}}try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static IllegalArgumentException stale(){return invalid("知识最新版本或状态已变化，请重新读取并确认");}
    private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
}
