package com.agri_system.application.auth.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 微信服务
 * 封装微信API调用
 * 
 * @author AI Generated
 */
@Service
public class WechatService {
    
    @Value("${wechat.app-id:}")
    private String appId;
    
    @Value("${wechat.app-secret:}")
    private String appSecret;
    
    private final WechatApiClient api;
    public WechatService(WechatApiClient api){this.api=api;}

    public WechatLoginResult jscode2session(String code) {
        if(appId==null||!appId.matches("wx[a-zA-Z0-9]{16}")||appSecret==null||appSecret.isBlank())throw new IllegalStateException("微信登录配置未完成");
        if(code==null||code.isBlank()||code.length()>512)throw new IllegalArgumentException("微信登录凭证无效");
        com.fasterxml.jackson.databind.JsonNode response;
        try{response=api.codeToSession(appId,appSecret,code);}
        catch(RuntimeException failure){throw new IllegalStateException("微信登录失败，请稍后重试");}
        if(response==null||!response.isObject())throw new IllegalStateException("微信登录失败：身份信息不完整");
        if(response.has("errcode")){
            if((!response.path("errcode").isIntegralNumber()||!response.path("errcode").canConvertToInt()))throw new IllegalStateException("微信登录失败：身份信息不完整");
            if(response.path("errcode").asInt()!=0)throw new IllegalStateException("微信登录失败：微信接口返回错误码 "+response.path("errcode").asInt());
        }
        String open=response.path("openid").isTextual()?response.path("openid").asText():null;
        String key=response.path("session_key").isTextual()?response.path("session_key").asText():null;
        if(open==null||open.isBlank()||open.length()>128||key==null||key.isBlank()||key.length()>255)throw new IllegalStateException("微信登录失败：身份信息不完整");
        var result=new WechatLoginResult();result.setAppId(appId);result.setOpenId(open);result.setSessionKey(key);
        result.setUnionId(response.path("unionid").isTextual()?response.path("unionid").asText():null);return result;
    }

    /**
     * 微信登录结果
     */
    @Data
    public static class WechatLoginResult {
        private String appId;
        private String openId;
        private String sessionKey;
        private String unionId;
    }
    
}
