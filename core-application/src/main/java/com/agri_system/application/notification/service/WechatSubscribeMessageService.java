package com.agri_system.application.notification.service;

import lombok.Data;
import org.springframework.stereotype.Service;

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
@Service
public class WechatSubscribeMessageService {

    private final com.agri_system.application.auth.service.WechatAccessTokenService tokens;
    private final com.agri_system.application.auth.service.WechatApiClient api;
    public WechatSubscribeMessageService(com.agri_system.application.auth.service.WechatAccessTokenService tokens,
                                        com.agri_system.application.auth.service.WechatApiClient api){this.tokens=tokens;this.api=api;}
    String getAccessToken(){try{return tokens.requireAccessToken();}catch(RuntimeException failure){return null;}}
    void invalidateAccessToken(){tokens.invalidate();}

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
            try{invalidateAccessToken();}catch(RuntimeException failure){return SendResult.fail("access_token 刷新未完成");}
            String refreshed = getAccessToken();
            if (refreshed != null) {
                result = doPost(refreshed, openid, templateId, page, data, miniprogramState);
            }
        }
        return result;
    }

    private SendResult doPost(String token, String openid, String templateId, String page,
                              Map<String, String> data, String miniprogramState) {
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
            var response=api.subscription(token,body);
            if(response==null||(!response.path("errcode").isIntegralNumber()||!response.path("errcode").canConvertToInt()))return SendResult.fail("微信响应无效");
            int error=response.path("errcode").asInt();
            return error==0?SendResult.ok():SendResult.of(error,"微信返回错误码 "+error);
        }catch(RuntimeException failure){return SendResult.fail("微信订阅消息发送未完成");}
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
