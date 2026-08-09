package com.agri_system.application.notification.service;

import com.agri_system.application.notification.service.WechatSubscribeMessageService.SendResponse;
import com.agri_system.application.notification.service.WechatSubscribeMessageService.SendResult;
import com.agri_system.application.notification.service.WechatSubscribeMessageService.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 微信订阅消息服务：access_token 的 Redis 缓存与发送重试。
 */
@DisplayName("微信订阅消息服务：access_token 缓存与发送")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WechatSubscribeMessageServiceTest {

    private static final String TOKEN_KEY = "wechat:access_token:APP_ID";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock RestTemplate restTemplate;

    private WechatSubscribeMessageService service;

    @BeforeEach
    void setUp() {
        service = new WechatSubscribeMessageService(redisTemplate);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "appId", "APP_ID");
        ReflectionTestUtils.setField(service, "appSecret", "APP_SECRET");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private TokenResponse tokenResponse(String token, long expiresIn) {
        TokenResponse r = new TokenResponse();
        r.setAccess_token(token);
        r.setExpires_in(expiresIn);
        return r;
    }

    private SendResponse sendResponse(int errcode, String errmsg) {
        SendResponse r = new SendResponse();
        r.setErrcode(errcode);
        r.setErrmsg(errmsg);
        return r;
    }

    @Test
    @DisplayName("缓存未命中：调微信刷新并回写 Redis（TTL = expires_in - 200s）")
    void cacheMissRefreshes() {
        when(valueOps.get(TOKEN_KEY)).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(TokenResponse.class)))
                .thenReturn(tokenResponse("TOKEN_1", 7200));

        String token = service.getAccessToken();

        assertEquals("TOKEN_1", token);
        verify(valueOps).set(eq(TOKEN_KEY), eq("TOKEN_1"),
                eq(Duration.ofSeconds(7200 - 200)));
    }

    @Test
    @DisplayName("缓存命中：直接返回，不调微信")
    void cacheHitNoRefresh() {
        when(valueOps.get(TOKEN_KEY)).thenReturn("TOKEN_CACHED");

        String token = service.getAccessToken();

        assertEquals("TOKEN_CACHED", token);
        verify(restTemplate, never()).getForObject(anyString(), any());
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("微信刷新失败(errcode 非空)：返回 null，不回写缓存")
    void refreshFailureReturnsNull() {
        when(valueOps.get(TOKEN_KEY)).thenReturn(null);
        when(restTemplate.getForObject(anyString(), eq(TokenResponse.class)))
                .thenReturn(tokenResponse(null, 0));

        String token = service.getAccessToken();

        assertNullToken(token);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("正常发送：缓存命中 + POST 成功，单次调用，不失效缓存")
    void sendSuccess() {
        when(valueOps.get(TOKEN_KEY)).thenReturn("TOKEN_OK");
        when(restTemplate.postForObject(anyString(), any(), eq(SendResponse.class)))
                .thenReturn(sendResponse(0, "ok"));

        SendResult result = service.send("OPENID", "TPL", "p", Map.of("thing1", "v"), "formal");

        assertTrue(result.isSuccess());
        verify(redisTemplate, never()).delete(anyString());
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(SendResponse.class));
    }

    @Test
    @DisplayName("发送遇 40001(token 失效)：失效缓存后重试一次并成功")
    void sendRetriesOn40001() {
        when(valueOps.get(TOKEN_KEY)).thenReturn("TOKEN_OLD");
        when(restTemplate.postForObject(anyString(), any(), eq(SendResponse.class)))
                .thenReturn(sendResponse(40001, "invalid token"))
                .thenReturn(sendResponse(0, "ok"));

        SendResult result = service.send("OPENID", "TPL", "p", Map.of("thing1", "v"), "formal");

        assertTrue(result.isSuccess());
        verify(redisTemplate).delete(TOKEN_KEY); // 失效缓存
        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(SendResponse.class));
    }

    @Test
    @DisplayName("access_token 获取失败(appId 为空)：send 直接返回失败，不发起 POST")
    void sendFailsWhenNoAppId() {
        WechatSubscribeMessageService blank = new WechatSubscribeMessageService(redisTemplate);
        ReflectionTestUtils.setField(blank, "appId", "");
        SendResult result = blank.send("OPENID", "TPL", "p", Map.of("thing1", "v"), "formal");

        assertFalse(result.isSuccess());
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    private static void assertNullToken(String token) {
        org.junit.jupiter.api.Assertions.assertNull(token);
    }
}
