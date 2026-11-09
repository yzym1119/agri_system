package com.agri_system.application.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

/** 手机号和订阅消息共享原AppID缓存，不维护另一份平台令牌。 */
@Service
public class WechatAccessTokenService {
    private final StringRedisTemplate redis;
    private final WechatApiClient api;
    @Value("${wechat.app-id:}") private String appId;
    @Value("${wechat.app-secret:}") private String secret;
    public WechatAccessTokenService(StringRedisTemplate redis,WechatApiClient api){this.redis=redis;this.api=api;}
    public String requireAccessToken(){
        if(appId==null||!appId.matches("wx[a-zA-Z0-9]{16}")||secret==null||secret.isBlank())throw new IllegalStateException("微信授权配置未完成");
        String key="wechat:access_token:"+appId;
        try {
            String cached=redis.opsForValue().get(key);if(cached!=null&&!cached.isBlank()&&cached.length()<=4096)return cached;
            var response=api.accessToken(appId,secret);
            if(response==null||response.has("errcode")&&((!response.path("errcode").isIntegralNumber()||!response.path("errcode").canConvertToInt())||response.path("errcode").asInt()!=0)
                    ||!response.path("access_token").isTextual()||response.path("access_token").asText().isBlank()||response.path("access_token").asText().length()>4096
                    ||!response.path("expires_in").isIntegralNumber()||!response.path("expires_in").canConvertToLong())throw new IllegalStateException();
            long expiry=response.path("expires_in").asLong();if(expiry<=0||expiry>86400)throw new IllegalStateException();
            String token=response.path("access_token").asText();redis.opsForValue().set(key,token,Duration.ofSeconds(Math.max(1,expiry-200)));return token;
        }catch(RuntimeException failure){throw new IllegalStateException("微信访问令牌获取失败，请稍后重试");}
    }
    public void invalidate(){
        if(appId!=null&&appId.matches("wx[a-zA-Z0-9]{16}")){
            try{redis.delete("wechat:access_token:"+appId);}catch(RuntimeException failure){throw new IllegalStateException("微信访问令牌刷新未完成，请稍后重试");}
        }
    }
}
