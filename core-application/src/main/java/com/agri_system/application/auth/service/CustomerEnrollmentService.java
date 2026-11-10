package com.agri_system.application.auth.service;

import com.agri_system.application.customer.dto.CustomerCreateCommand;
import com.agri_system.application.customer.service.CustomerRegistrationService;
import com.agri_system.domain.auth.model.CustomerIdentity;
import com.agri_system.domain.auth.model.CustomerIdentityEnrollment;
import com.agri_system.domain.auth.repository.CustomerIdentityEnrollmentRepository;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CustomerEnrollmentService {
    private final ClientOnboardingService onboarding;
    private final CustomerIdentityEnrollmentRepository requests;
    private final CustomerIdentityRepository identities;
    private final CustomerRepository customers;
    private final CustomerRegistrationService registration;
    private final PlatformTransactionManager transactions;
    public record Register(@jakarta.validation.constraints.NotBlank String requestKey,@jakarta.validation.constraints.Size(max=100) String displayName){
        @Override public String toString(){return "CustomerEnrollmentRegister[申请内容隐藏]";}
    }
    public record Result(String requestKey,String status,String nextAction){}
    public Result register(Register command){
        key(command.requestKey());String name=name(command.displayName());var proof=onboarding.requireVerifiedPhone();
        if(TransactionSynchronizationManager.isActualTransactionActive()||com.mybatisflex.core.transaction.TransactionContext.getXID()!=null)
            throw new IllegalStateException("开户入口必须独立调用，以便确定回滚后核对原请求");
        var tx=new TransactionTemplate(transactions);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        for(int attempt=0;attempt<5;attempt++){
            if(!proof.equals(onboarding.requireVerifiedPhone()))throw new IllegalArgumentException("授权证据已改变，请重新核验");
            try{return tx.execute(status->registerInTransaction(command,name,proof));}
            catch(RuntimeException failure){
                if(!deadlockRolledBack(failure)||attempt==4||Thread.currentThread().isInterrupted())throw failure;
                // 只重试MySQL明确整笔回滚的1213；未知提交/连接异常不得盲重试。
                java.util.concurrent.locks.LockSupport.parkNanos((attempt+1)*5_000_000L);
            }
        }
        throw new IllegalStateException("开户申请结果待核对");
    }
    private Result registerInTransaction(Register command,String name,ClientOnboardingService.VerifiedPhone proof){
        var proposed=new CustomerIdentityEnrollment();proposed.setRequestKey(command.requestKey());proposed.setAppId(proof.appId());proposed.setOpenId(proof.openId());proposed.setPhone(proof.phone());proposed.setDisplayName(name);
        proposed.setFingerprint(hash(proof.appId(),proof.openId(),proof.phone(),name));proposed.setActiveSubject(hash(proof.appId(),proof.openId()));
        proposed.setVerifiedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(proof.verifiedAt()),ZoneId.systemDefault()));proposed.setStatus("PROCESSING");
        var row=requests.acquire(proposed);owned(row,proof);
        if(!Objects.equals(row.getFingerprint(),proposed.getFingerprint())||!Objects.equals(row.getPhone(),proof.phone())||!Objects.equals(row.getDisplayName(),name))throw new IllegalArgumentException("原申请内容不能更改，请先查询原结果");
        if(!proof.equals(onboarding.requireVerifiedPhone()))throw new IllegalArgumentException("授权证据已改变，请重新核验");
        if(!"PROCESSING".equals(row.getStatus()))return view(row);
        if(identities.findWechatByAppAndOpenIdForUpdate(proof.appId(),proof.openId()).isPresent())throw new IllegalArgumentException("微信已有档案关联，请重新微信登录并核对");
        if(!customers.findIdsByPhoneIncludingDeleted(proof.phone()).isEmpty()){
            row.setStatus("REVIEW_REQUIRED");row.setReviewReason("PHONE_MATCH");
        }else{
            var input=new CustomerCreateCommand();input.setName(name);input.setPhone(proof.phone());var customer=registration.create(input);
            var identity=new CustomerIdentity();identity.setCustomerId(customer.getId());identity.setIdentityType("WECHAT_OPENID");identity.setAppId(proof.appId());identity.setIdentityValue(proof.openId());identity.setStatus(1);
            identity=identities.save(identity);row.setCustomerId(customer.getId());row.setIdentityId(identity.getId());row.setIdentityAuthVersion(identity.getAuthVersion());row.setStatus("CREATED");row.setReviewReason("NO_PHONE_MATCH");
        }
        requests.finishRegistration(row);
        if(!proof.equals(onboarding.requireVerifiedPhone()))throw new IllegalArgumentException("授权证据已改变，请查询原申请");return view(row);
    }
    public Result result(String requestKey){
        key(requestKey);var subject=onboarding.requireRegistrationSubject();var row=requests.findByRequestKey(requestKey,false).orElse(null);
        if(row==null)return new Result(requestKey,"UNKNOWN","RETRY_SAME_REQUEST");
        owned(row,subject.appId(),subject.openId());return view(row);
    }
    static void owned(CustomerIdentityEnrollment row,ClientOnboardingService.VerifiedPhone proof){owned(row,proof.appId(),proof.openId());}
    private static void owned(CustomerIdentityEnrollment row,String app,String open){if(!app.equals(row.getAppId())||!open.equals(row.getOpenId()))throw new IllegalArgumentException("申请不存在或不属于当前微信，请核对原请求");}
    static Result view(CustomerIdentityEnrollment row){
        String status=row.getStatus();String next=switch(status){case "CREATED","LINKED"->"LOGIN";case "REVIEW_REQUIRED"->"CONTACT_STORE";case "REJECTED"->"RESTART";default->throw new IllegalStateException("申请结果待核对");};return new Result(row.getRequestKey(),status,next);
    }
    private static boolean deadlockRolledBack(Throwable failure){
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql&&sql.getErrorCode()==1213&&"40001".equals(sql.getSQLState()))return true;
        return false;
    }
    static String name(String value){String text=value==null||value.isBlank()?"微信客户":value.strip();if(text.length()>100||text.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("客户姓名无效");return text;}
    static void key(String value){if(value==null||!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))throw new IllegalArgumentException("需要安全UUID请求号");}
    static String hash(String... parts){
        var canonical=new StringBuilder();for(String part:parts)canonical.append(part.length()).append(':').append(part);
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException("无法生成请求摘要");}
    }
}
