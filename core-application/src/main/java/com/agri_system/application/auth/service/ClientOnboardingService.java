package com.agri_system.application.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.SaLoginModel;
import com.agri_system.application.auth.dto.ClientWechatLoginCommand;
import com.agri_system.application.auth.dto.LoginResultVO;
import com.agri_system.domain.auth.repository.CustomerIdentityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/** 短时身份/号码核验；不创建客户或钱包，不授予旧档案资源。 */
@Service
public class ClientOnboardingService {
    private final WechatService wechat;
    private final WechatPhoneAuthorizationService phones;
    private final CustomerIdentityRepository identities;
    private final AuthService authentication;
    @Value("${wechat.app-id:}") private String appId;
    public ClientOnboardingService(WechatService wechat,WechatPhoneAuthorizationService phones,CustomerIdentityRepository identities,AuthService authentication){
        this.wechat=wechat;this.phones=phones;this.identities=identities;this.authentication=authentication;
    }
    public record Reply(String state,LoginResultVO login,String tokenName,String tokenValue,Long expiresAt,String phoneMasked) {
        @Override public String toString(){return "WechatOnboardingReply[state="+state+", 凭据隐藏]";}
    }
    @Transactional(rollbackFor=Exception.class,isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Reply prepare(ClientWechatLoginCommand command){
        var identity=wechat.jscode2session(command.getCode());
        if(identity==null||!validApp()||!appId.equals(identity.getAppId())||identity.getOpenId()==null||identity.getOpenId().isBlank()||identity.getOpenId().length()>128
                ||identity.getSessionKey()==null||identity.getSessionKey().isBlank())throw invalid();
        if(identities.findWechatByAppAndOpenIdForUpdate(appId,identity.getOpenId()).isPresent())
            return new Reply("LINKED",authentication.loginVerifiedWechat(identity),null,null,null,null);
        return issue(Map.of("kind","WECHAT_ONBOARDING","appId",appId,"openId",identity.getOpenId(),"state","PHONE_REQUIRED","expiresAt",Instant.now().getEpochSecond()+600));
    }
    public Reply authorizePhone(ClientWechatLoginCommand command){
        var source=requireFlow();if(!"PHONE_REQUIRED".equals(source.get("state")))throw new IllegalArgumentException("手机号授权已经完成，请继续原流程");
        String sourceToken=StpUtil.getTokenValue();
        String open=(String)source.get("openId");var evidence=phones.authorize(appId,open,command.getCode());
        if(!sourceToken.equals(StpUtil.getTokenValue())||!source.equals(requireFlow()))throw invalid();
        if(evidence==null||!appId.equals(evidence.appId())||!open.equals(evidence.openId())||evidence.phone()==null||!evidence.phone().matches("1[3-9]\\d{9}"))throw invalid();
        var next=new HashMap<String,Object>(source);next.put("state","PHONE_VERIFIED");next.put("phone",evidence.phone());next.put("verifiedAt",evidence.verifiedAt());
        // 不覆写原凭据，异步并发结果各属自己的凭据；不延长原授权截止时间。
        return issue(Map.copyOf(next));
    }
    public Reply info(){return view(requireFlow(),null);}
    public record VerifiedPhone(String appId,String openId,String phone,long verifiedAt){
        @Override public String toString(){return "VerifiedPhone[核验内容隐藏]";}
    }
    /** 应用层核验入口，不接受客户端自报号码或身份。 */
    public VerifiedPhone requireVerifiedPhone(){
        var source=requireFlow();if(!"PHONE_VERIFIED".equals(source.get("state")))throw invalid();
        long verified=((Number)source.get("verifiedAt")).longValue(),now=Instant.now().getEpochSecond();
        if(verified<=0||verified<now-600||verified>now+60)throw invalid();
        return new VerifiedPhone(appId,(String)source.get("openId"),(String)source.get("phone"),verified);
    }
    public record RegistrationSubject(String appId,String openId){}
    /** 查原申请只需真实同微信身份；重登后不要求重复付出手机号授权。 */
    public RegistrationSubject requireRegistrationSubject(){
        if(StpUtil.isLogin()&&StpUtil.getLoginIdAsString().startsWith("ONBOARDING_")){
            var source=requireFlow();return new RegistrationSubject((String)source.get("appId"),(String)source.get("openId"));
        }
        Long customerId=authentication.getCurrentCustomerId();Object marker=StpUtil.getTokenSession().get("clientAuthV1");
        if(!(marker instanceof Map<?,?> binding)||!"WECHAT".equals(binding.get("kind"))||!validApp()||!appId.equals(binding.get("appId")))throw invalid();
        Long id,version;try{id=Long.valueOf(String.valueOf(binding.get("identityId")));version=Long.valueOf(String.valueOf(binding.get("authVersion")));}catch(NumberFormatException malformed){throw invalid();}
        var identity=identities.findById(id).orElseThrow(ClientOnboardingService::invalid);
        if(id<=0||version<=0||!identity.isActive()||!customerId.equals(identity.getCustomerId())||!appId.equals(identity.getAppId())||!identity.isWechatOpenId()
                ||!version.equals(identity.getAuthVersion())||identity.getIdentityValue()==null||identity.getIdentityValue().isBlank())throw invalid();
        return new RegistrationSubject(appId,identity.getIdentityValue());
    }
    private Reply issue(Map<String,Object> evidence){
        long remaining=((Number)evidence.get("expiresAt")).longValue()-Instant.now().getEpochSecond();if(remaining<=0||remaining>600)throw invalid();
        String token=UUID.randomUUID().toString();StpUtil.login("ONBOARDING_"+UUID.randomUUID(),new SaLoginModel().setToken(token).setTimeout(remaining));
        StpUtil.getTokenSession().set("onboardingV1",evidence);return view(evidence,token);
    }
    private Map<String,Object> requireFlow(){
        if(!StpUtil.isLogin()||!StpUtil.getLoginIdAsString().matches("ONBOARDING_[0-9a-f-]{36}")||!validApp()
                ||!StpUtil.getLoginIdAsString().equals(StpUtil.getLoginIdByToken(StpUtil.getTokenValue())))throw invalid();
        Object raw=StpUtil.getTokenSession().get("onboardingV1");if(!(raw instanceof Map<?,?> source)||!"WECHAT_ONBOARDING".equals(source.get("kind"))||!appId.equals(source.get("appId"))
                ||!(source.get("openId") instanceof String open)||open.isBlank()||open.length()>128||!(source.get("expiresAt") instanceof Number expiry))throw invalid();
        long remaining=expiry.longValue()-Instant.now().getEpochSecond();if(remaining<=0||remaining>600)throw invalid();
        String state=String.valueOf(source.get("state"));if(!"PHONE_REQUIRED".equals(state)&&!"PHONE_VERIFIED".equals(state))throw invalid();
        if("PHONE_VERIFIED".equals(state)&&(!(source.get("phone") instanceof String phone)||!phone.matches("1[3-9]\\d{9}")||!(source.get("verifiedAt") instanceof Number)))throw invalid();
        var result=new HashMap<String,Object>();source.forEach((key,value)->{if(key instanceof String text)result.put(text,value);});return Map.copyOf(result);
    }
    private Reply view(Map<String,Object> source,String token){
        String phone=source.get("phone") instanceof String p?p:null;
        return new Reply(String.valueOf(source.get("state")),null,token==null?null:StpUtil.getTokenName(),token,((Number)source.get("expiresAt")).longValue(),phone==null?null:phone.substring(0,3)+"****"+phone.substring(7));
    }
    private boolean validApp(){return appId!=null&&appId.matches("wx[a-zA-Z0-9]{16}");}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("微信授权流程已失效，请重新授权");}
}
