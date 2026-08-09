package com.agri_system.application.notification.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序订阅消息发送服务。
 * <p>
 * 职责：access_token 的 Redis 缓存（避免高频拉取被微信限流）+ 调用
 * {@code cgi-bin/message/subscribe/send}。access_token 失效(errcode 40001)时失效缓存并重试一次。
 * <p>
 * 复用既有 {@code wechat.app-id} / {@code wechat.app-secret}（与 {@code WechatService} 同源）。
 *
 * @author AI Generated
 */
@Slf4j
@Service
public class WechatSubscribeMessageService {

    private static final String TOKEN_CACHE_PREFIX = "wechat:access_token:";
    private static final long TOKEN_SAFETY_MARGIN_SECONDS = 200L;

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;

    @Value("${wechat.app-id:}")
    private String appId;

    @Value("${wechat.app-secret:}")
    private String appSecret;

    public WechatSubscribeMessageService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 获取 access_token：优先 Redis 缓存，未命中则向微信刷新并回写（TTL = expires_in - 200s）。
     *
     * @return access_token，或获取失败时 null
     */
    String getAccessToken() {
        if (appId == null || appId.isBlank()) {
            return null;
        }
        String key = TOKEN_CACHE_PREFIX + appId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        return refreshAccessToken(key);
    }

    private String refreshAccessToken(String key) {
        String url = String.format(
                "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                appId, appSecret);
        try {
            TokenResponse resp = restTemplate.getForObject(url, TokenResponse.class);
            if (resp == null || resp.getAccess_token() == null || resp.getAccess_token().isBlank()) {
                log.warn("微信 access_token 刷新失败: errcode={} errmsg={}",
                        resp == null ? null : resp.getErrcode(),
                        resp == null ? null : resp.getErrmsg());
                return null;
            }
            long ttl = resp.getExpires_in() != null ? resp.getExpires_in() : 7200L;
            long ttlSec = Math.max(60L, ttl - TOKEN_SAFETY_MARGIN_SECONDS);
            redisTemplate.opsForValue().set(key, resp.getAccess_token(), Duration.ofSeconds(ttlSec));
            return resp.getAccess_token();
        } catch (Exception e) {
            log.warn("微信 access_token 刷新异常: {}", e.getMessage());
            return null;
        }
    }

    /** 失效 access_token 缓存（errcode 40001 时调用，便于下次重新获取）。 */
    void invalidateAccessToken() {
        if (appId != null && !appId.isBlank()) {
            redisTemplate.delete(TOKEN_CACHE_PREFIX + appId);
        }
    }

    /**
     * 发送一条订阅消息。access_token 失效(40001)时失效缓存并重试一次。
     * 本方法不抛异常，结果统一以 {@link SendResult} 返回，供调用方记录日志。
     *
     * @param openid           接收者 openid
     * @param templateId       模板ID
     * @param page             跳转页面（可空）
     * @param data             模板数据：微信字段(如 thing1) → 值
     * @param miniprogramState 小程序版本状态
     * @return 发送结果
     */
    public SendResult send(String openid, String templateId, String page,
                           Map<String, String> data, String miniprogramState) {
        String token = getAccessToken();
        if (token == null) {
            return SendResult.fail("access_token 获取失败");
        }
        SendResult result = doPost(token, openid, templateId, page, data, miniprogramState);
        if (result.isTokenInvalid()) {
            invalidateAccessToken();
            String refreshed = getAccessToken();
            if (refreshed != null) {
                result = doPost(refreshed, openid, templateId, page, data, miniprogramState);
            }
        }
        return result;
    }

    private SendResult doPost(String token, String openid, String templateId, String page,
                              Map<String, String> data, String miniprogramState) {
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + token;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", openid);
        body.put("template_id", templateId);
        if (page != null && !page.isBlank()) {
            body.put("page", page);
        }
        if (miniprogramState != null && !miniprogramState.isBlank()) {
            body.put("miniprogram_state", miniprogramState);
        }
        // 微信要求 data 形如 {"thing1": {"value": "x"}, "amount2": {"value": "10"}}
        Map<String, Object> wrapped = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((k, v) -> wrapped.put(k, Map.of("value", v == null ? "" : v)));
        }
        body.put("data", wrapped);
        try {
            SendResponse resp = restTemplate.postForObject(url, body, SendResponse.class);
            if (resp == null) {
                return SendResult.fail("微信响应为空");
            }
            if (resp.getErrcode() != null && resp.getErrcode() == 0) {
                return SendResult.ok();
            }
            return SendResult.of(resp.getErrcode(), resp.getErrmsg());
        } catch (Exception e) {
            return SendResult.fail("微信订阅消息发送异常: " + e.getMessage());
        }
    }

    @Data
    static class TokenResponse {
        private String access_token;
        private Long expires_in;
        private Integer errcode;
        private String errmsg;
    }

    @Data
    static class SendResponse {
        private Integer errcode;
        private String errmsg;
    }

    /**
     * 订阅消息发送结果。
     */
    @Data
    public static class SendResult {
        private boolean success;
        /** 用户未订阅/拒收(43101) —— 视为正常跳过，不计为失败。 */
        private boolean skipped;
        private Integer errcode;
        private String message;

        static SendResult ok() {
            SendResult r = new SendResult();
            r.success = true;
            return r;
        }

        static SendResult fail(String msg) {
            SendResult r = new SendResult();
            r.message = msg;
            return r;
        }

        static SendResult of(Integer errcode, String errmsg) {
            SendResult r = new SendResult();
            r.errcode = errcode;
            r.message = errmsg;
            // 43101 = 用户拒收/未订阅，属预期内的正常跳过
            if (errcode != null && errcode == 43101) {
                r.skipped = true;
            }
            return r;
        }

        boolean isTokenInvalid() {
            return errcode != null && errcode == 40001;
        }
    }
}
