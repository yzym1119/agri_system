package com.agri_system.application.auth.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 微信服务
 * 封装微信API调用
 * 
 * @author AI Generated
 */
@Slf4j
@Service
public class WechatService {
    
    @Value("${wechat.app-id:}")
    private String appId;
    
    @Value("${wechat.app-secret:}")
    private String appSecret;
    
    private final RestTemplate restTemplate;
    
    public WechatService() {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();factory.setConnectTimeout(5000);factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }
    
    /**
     * 微信登录：通过code获取OpenID和SessionKey
     * 
     * @param code 微信code
     * @return 微信登录结果
     */
    public WechatLoginResult jscode2session(String code) {
        if(appId==null||!appId.matches("wx[a-zA-Z0-9]{16}")||appSecret==null||appSecret.isBlank())throw new IllegalStateException("微信登录配置未完成");
        if(code==null||code.isBlank()||code.length()>512)throw new IllegalArgumentException("微信登录凭证无效");
        // appid/secret/js_code 走参数化拼接，避免 code 含 & 等字符时改写查询串或污染日志
        String url = UriComponentsBuilder.fromUriString("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .encode()
                .build()
                .toUriString();
        
        // 只对 HTTP 调用本身做兜底脱敏：微信返回的业务错误（errcode）不含凭据，
        // 但仍不把服务端自由文本 errmsg 回传或落库，只保留可核对的错误码。
        WechatResponse response;
        try {
            response = restTemplate.getForObject(url, WechatResponse.class);
        } catch (Exception e) {
            // 微信 code 与 AppSecret 都等同于凭据；RestTemplate 的异常消息通常带完整请求 URL。
            // 日志只留脱敏摘要与异常类型，连消息都不记录——字符串替换无法覆盖所有异常格式。
            log.error("微信登录失败：code={}, errorType={}", maskCode(code), e.getClass().getName());
            throw sanitizedFailure(e);
        }

        if (response == null) {
            throw new RuntimeException("微信登录失败：微信接口无响应");
        }

        if (response.getErrcode() != null && response.getErrcode() != 0) {
            log.warn("微信登录失败：code={}, errcode={}", maskCode(code), response.getErrcode());
            throw new RuntimeException("微信登录失败：微信接口返回错误码 " + response.getErrcode());
        }

        if(response.getOpenid()==null||response.getOpenid().isBlank()||response.getOpenid().length()>128||response.getSession_key()==null||response.getSession_key().isBlank()||response.getSession_key().length()>255)
            throw new IllegalStateException("微信登录失败：身份信息不完整");

        WechatLoginResult result = new WechatLoginResult();
        result.setAppId(appId);
        result.setOpenId(response.getOpenid());
        result.setSessionKey(response.getSession_key());
        result.setUnionId(response.getUnionid());

        return result;
    }

    /** 用固定文案重建异常并保留原调用栈，向上抛出时不携带含 AppSecret/code 的 URL 或平台原始文本。 */
    private RuntimeException sanitizedFailure(Exception cause) {
        RuntimeException failure = new RuntimeException("微信登录失败，请稍后重试");
        failure.setStackTrace(cause.getStackTrace());
        return failure;
    }

    /** code 脱敏：仅保留前 4 位与长度，中间以 *** 代替。 */
    private String maskCode(String code) {
        if (code == null || code.length() <= 4) {
            return "***";
        }
        return code.substring(0, 4) + "***(" + code.length() + ")";
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
    
    /** 微信API响应（包内可见：便于同包测试构造平台返回） */
    @Data
    static class WechatResponse {
        private String openid;
        private String session_key;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }
}

