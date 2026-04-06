package com.agri_system.application.auth.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 微信服务
 * 封装微信API调用
 * 
 * @author AI Generated
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatService {
    
    @Value("${wechat.app-id:}")
    private String appId;
    
    @Value("${wechat.app-secret:}")
    private String appSecret;
    
    private final RestTemplate restTemplate;
    
    public WechatService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 微信登录：通过code获取OpenID和SessionKey
     * 
     * @param code 微信code
     * @return 微信登录结果
     */
    public WechatLoginResult jscode2session(String code) {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appId, appSecret, code
        );
        
        try {
            WechatResponse response = restTemplate.getForObject(url, WechatResponse.class);
            
            if (response == null) {
                throw new RuntimeException("微信API调用失败：响应为空");
            }
            
            if (response.getErrcode() != null && response.getErrcode() != 0) {
                throw new RuntimeException("微信API调用失败：" + response.getErrmsg());
            }
            
            WechatLoginResult result = new WechatLoginResult();
            result.setOpenId(response.getOpenid());
            result.setSessionKey(response.getSession_key());
            result.setUnionId(response.getUnionid());
            
            return result;
        } catch (Exception e) {
            log.error("微信登录失败：code={}, error={}", code, e.getMessage(), e);
            throw new RuntimeException("微信登录失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 微信登录结果
     */
    @Data
    public static class WechatLoginResult {
        private String openId;
        private String sessionKey;
        private String unionId;
    }
    
    /**
     * 微信API响应
     */
    @Data
    private static class WechatResponse {
        private String openid;
        private String session_key;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }
}

