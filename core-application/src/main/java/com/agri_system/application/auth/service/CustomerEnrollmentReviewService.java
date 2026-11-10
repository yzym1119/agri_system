package com.agri_system.application.auth.service;

import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.model.CustomerIdentityEnrollment;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.domain.auth.repository.CustomerIdentityEnrollmentRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.store.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

/** 人工核验仅创建初次身份关联；不改老档案手机号、钱货或历史单据归属。 */
@Service
@RequiredArgsConstructor
public class CustomerEnrollmentReviewService {
    private final CustomerIdentityEnrollmentRepository requests;
    private final CustomerIdentityRepository identities;
    private final CustomerRepository customers;
    private final StaffRepository staff;
    public record Decision(@jakarta.validation.constraints.NotBlank String requestKey,@jakarta.validation.constraints.NotBlank String action,Long targetCustomerId,
                           @jakarta.validation.constraints.NotBlank String verificationMethod,@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=500) String reason){
        @Override public String toString(){return "CustomerEnrollmentDecision[核验内容隐藏]";}
    }
    public record Summary(String id,String requestKey,String displayName,String phoneMasked,String status,String reviewReason,String createdAt){}
    public record Page(List<Summary> rows,String nextBefore){}
    public record Candidate(String id,String name,String phoneMasked,Integer status,Integer deleted){}
    public record Detail(Summary request,String appId,String openIdMasked,String verifiedAt,List<Candidate> candidates,String customerId,String identityId,String identityAuthVersion,String reviewedBy,String verificationMethod,String reason,String reviewedAt){}
    public record DecisionResult(String enrollmentId,String requestKey,String status,String action,String targetCustomerId,String verificationMethod,String reason){
        @Override public String toString(){return "CustomerEnrollmentDecisionResult[核验内容隐藏]";}
    }
    /** UNKNOWN不证明未执行；只返回本员工、原决定号的完整提交结果，不借用另一人的决定。 */
    public DecisionResult decisionResult(Long staffId,Long id,String key){
        actor(staffId);CustomerEnrollmentService.key(key);var row=request(id,false);
        if(row.getDecisionRequestKey()==null&&"REVIEW_REQUIRED".equals(row.getStatus()))return new DecisionResult(text(id),key,"UNKNOWN",null,null,null,null);
        if(!Objects.equals(key,row.getDecisionRequestKey())||!Objects.equals(staffId,row.getReviewedBy()))return new DecisionResult(text(id),key,"CONFLICT",null,null,null,null);
        String action="LINKED".equals(row.getStatus())?"LINK":"REJECTED".equals(row.getStatus())?"REJECT":null;
        if(action==null||row.getVerificationMethod()==null||row.getDecisionReason()==null||row.getDecisionReason().isBlank())throw new IllegalStateException("原核验审计不完整，请联系管理员核对");
        String fingerprint=CustomerEnrollmentService.hash(text(id),action,String.valueOf(row.getCustomerId()),row.getVerificationMethod(),row.getDecisionReason(),text(staffId));
        if(action==null||"LINK".equals(action)&&(row.getCustomerId()==null||row.getIdentityId()==null||row.getIdentityAuthVersion()==null)
                ||!Objects.equals(fingerprint,row.getDecisionFingerprint()))throw new IllegalStateException("原核验审计不完整，请联系管理员核对");
        return new DecisionResult(text(id),key,row.getStatus(),action,text(row.getCustomerId()),row.getVerificationMethod(),row.getDecisionReason());
    }
    public List<Candidate> searchCandidates(Long staffId,String keyword){
        actor(staffId);String term=keyword==null?"":keyword.strip();
        if(term.length()<2||term.length()>50||term.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("请输入2至50字的原客户姓名或手机号");
        return customers.findActiveByKeyword(term,20).stream().map(c->new Candidate(text(c.getId()),c.getName(),mask(c.getPhone()),c.getStatus(),c.getDeleted())).toList();
    }
    public Page list(Long staffId,String status,Long before){
        actor(staffId);String selected=status==null?"REVIEW_REQUIRED":status;
        if(!List.of("REVIEW_REQUIRED","CREATED","LINKED","REJECTED").contains(selected)||before!=null&&before<=0)throw new IllegalArgumentException("申请查询条件无效");
        var rows=requests.page(selected,before,21);var page=rows.subList(0,Math.min(20,rows.size()));
        return new Page(page.stream().map(this::summary).toList(),rows.size()>20?page.get(page.size()-1).getId().toString():null);
    }
    public Detail detail(Long staffId,Long id){
        actor(staffId);var row=request(id,false);
        var candidates=customers.findIdsByPhoneIncludingDeleted(row.getPhone()).stream().map(customers::findByIdIncludingDeleted).flatMap(java.util.Optional::stream)
                .map(c->new Candidate(text(c.getId()),c.getName(),mask(c.getPhone()),c.getStatus(),c.getDeleted())).toList();
        String open=row.getOpenId();return new Detail(summary(row),row.getAppId(),open==null||open.length()<5?"***":"***"+open.substring(open.length()-4),time(row.getVerifiedAt()),candidates,
                text(row.getCustomerId()),text(row.getIdentityId()),text(row.getIdentityAuthVersion()),text(row.getReviewedBy()),row.getVerificationMethod(),row.getDecisionReason(),time(row.getReviewedAt()));
    }
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CustomerEnrollmentService.Result decide(Long staffId,Long id,Decision command){
        actor(staffId);CustomerEnrollmentService.key(command.requestKey());String reason=command.reason()==null?"":command.reason().strip();
        if(reason.isEmpty()||reason.length()>500||reason.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException("请填写有效核验原因");
        boolean link="LINK".equals(command.action());
        if(!link&&!"REJECT".equals(command.action())||link&&(command.targetCustomerId()==null||command.targetCustomerId()<=0)
                ||!link&&command.targetCustomerId()!=null||command.verificationMethod()==null||!List.of("IN_PERSON","KNOWN_CONTACT_CALLBACK","UNCONFIRMED").contains(command.verificationMethod())
                ||link&&"UNCONFIRMED".equals(command.verificationMethod()))throw new IllegalArgumentException("核验决定、对象或方式无效");
        String fingerprint=CustomerEnrollmentService.hash(String.valueOf(id),command.action(),String.valueOf(command.targetCustomerId()),command.verificationMethod(),reason,staffId.toString());
        var row=request(id,true);
        if(row.getDecisionRequestKey()!=null){
            if(Objects.equals(row.getDecisionRequestKey(),command.requestKey())&&Objects.equals(row.getDecisionFingerprint(),fingerprint)&&!"REVIEW_REQUIRED".equals(row.getStatus()))return CustomerEnrollmentService.view(row);
            throw new IllegalArgumentException("原核验请求或结果不能更改，请查询原决定");
        }
        if(!"REVIEW_REQUIRED".equals(row.getStatus()))throw new IllegalArgumentException("申请已经处理，不能覆盖原结果");
        row.setDecisionRequestKey(command.requestKey());row.setDecisionFingerprint(fingerprint);row.setReviewedBy(staffId);row.setVerificationMethod(command.verificationMethod());row.setDecisionReason(reason);
        requests.claimDecision(row);
        if(link){
            var target=customers.findByIdWithLock(command.targetCustomerId()).orElseThrow(()->new IllegalArgumentException("目标客户不存在或已删除"));
            if(!target.isActive()||Integer.valueOf(1).equals(target.getDeleted()))throw new IllegalArgumentException("目标客户已停用或删除");
            if(identities.findWechatByAppAndOpenIdForUpdate(row.getAppId(),row.getOpenId()).isPresent()||!identities.findWechatByCustomerAndApp(target.getId(),row.getAppId()).isEmpty())
                throw new IllegalArgumentException("微信或目标客户已有同应用关联，请先核对身份冲突");
            var identity=new CustomerIdentity();identity.setAppId(row.getAppId());identity.setIdentityType("WECHAT_OPENID");identity.setIdentityValue(row.getOpenId());identity.setCustomerId(target.getId());identity.setStatus(1);identity=identities.save(identity);
            row.setCustomerId(target.getId());row.setIdentityId(identity.getId());row.setIdentityAuthVersion(identity.getAuthVersion());row.setStatus("LINKED");
        }else{row.setStatus("REJECTED");row.setActiveSubject(null);}
        requests.finishDecision(row);return CustomerEnrollmentService.view(row);
    }
    private CustomerIdentityEnrollment request(Long id,boolean lock){if(id==null||id<=0)throw new IllegalArgumentException("申请编号无效");return requests.findById(id,lock).orElseThrow(()->new IllegalArgumentException("申请不存在"));}
    private void actor(Long id){var current=id==null?null:staff.findIdentityById(id).orElse(null);if(current==null||!current.isActive()||current.hasBeenDeleted())throw new IllegalArgumentException("需要当前有效员工身份");}
    private Summary summary(CustomerIdentityEnrollment r){return new Summary(text(r.getId()),r.getRequestKey(),r.getDisplayName(),mask(r.getPhone()),r.getStatus(),r.getReviewReason(),time(r.getCreatedAt()));}
    private static String text(Long value){return value==null?null:value.toString();}
    private static String time(java.time.LocalDateTime value){return value==null?null:value.toString();}
    private static String mask(String phone){
        return com.agri_system.domain.customer.service.CustomerPhoneNumbers.mainland(phone)
                .map(value->value.substring(0,3)+"****"+value.substring(7)).orElse("联系方式待核对");
    }
}
