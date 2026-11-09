package com.agri_system.application.auth.service;

import org.springframework.stereotype.Service;
import java.time.Instant;

/** 核验用户主动授权的号码；号码证据本身不授予客户旧账访问权。 */
@Service
public class WechatPhoneAuthorizationService {
    private final WechatApiClient api;
    private final WechatAccessTokenService tokens;
    public WechatPhoneAuthorizationService(WechatApiClient api,WechatAccessTokenService tokens){this.api=api;this.tokens=tokens;}
    public record PhoneEvidence(String appId,String openId,String phone,long verifiedAt){
        @Override public String toString(){return "PhoneEvidence[已核验，内容隐藏]";}
    }
    public PhoneEvidence authorize(String appId,String openId,String code){
        if(appId==null||!appId.matches("wx[a-zA-Z0-9]{16}")||openId==null||openId.isBlank()||openId.length()>128||code==null||code.isBlank()||code.length()>512)
            throw new IllegalArgumentException("手机号授权凭证无效");
        var response=api.phoneNumber(tokens.requireAccessToken(),code,openId);
        if(response==null||(!response.path("errcode").isIntegralNumber()||!response.path("errcode").canConvertToInt()))throw invalid();
        int error=response.path("errcode").asInt();
        if(error!=0){if(error==40001||error==40014||error==42001)tokens.invalidate();throw new IllegalStateException("手机号授权未完成，请重新授权（错误码"+error+"）");}
        var phone=response.path("phone_info");var watermark=phone.path("watermark");
        if(!phone.path("purePhoneNumber").isTextual()||!phone.path("phoneNumber").isTextual()||!phone.path("countryCode").isTextual())throw invalid();
        String number=phone.path("purePhoneNumber").asText(),full=phone.path("phoneNumber").asText();
        if(!"86".equals(phone.path("countryCode").asText())||!number.matches("1[3-9]\\d{9}")||!(number.equals(full)||("86"+number).equals(full)||("+86"+number).equals(full))||!appId.equals(watermark.path("appid").asText())
                ||!watermark.path("timestamp").isIntegralNumber()||!watermark.path("timestamp").canConvertToLong())throw invalid();
        long now=Instant.now().getEpochSecond(),timestamp=watermark.path("timestamp").asLong();
        if(timestamp<=0||timestamp<now-600||timestamp>now+60)throw invalid();
        return new PhoneEvidence(appId,openId,number,now);
    }
    private static IllegalStateException invalid(){return new IllegalStateException("手机号授权资料不完整或已过期，请重新授权");}
}
