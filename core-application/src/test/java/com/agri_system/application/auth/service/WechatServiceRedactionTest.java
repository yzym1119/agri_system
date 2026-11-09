package com.agri_system.application.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 微信登录失败时不得把 AppSecret/code 或平台原始文本带进日志与异常消息。 */
class WechatServiceRedactionTest {
    private static final String SECRET = "app-secret-value";
    private static final String CODE = "wx-code-9f8e7d";
    private static final String URL = "https://api.weixin.qq.com/sns/jscode2session?appid=wx123"
            + "&secret=" + SECRET + "&js_code=" + CODE + "&grant_type=authorization_code";

    private WechatService serviceWith(RestTemplate restTemplate) {
        WechatService service = new WechatService();
        ReflectionTestUtils.setField(service, "appId", "wx1111111111111111");
        ReflectionTestUtils.setField(service, "appSecret", SECRET);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        return service;
    }

    @Test void incompleteSuccessfulResponseMustNotBecomeAnAuthenticatedIdentity() {
        var http=mock(RestTemplate.class);var response=new WechatService.WechatResponse();response.setErrcode(0);
        when(http.getForObject(anyString(),eq(WechatService.WechatResponse.class))).thenReturn(response);
        assertThrows(IllegalStateException.class,()->serviceWith(http).jscode2session(CODE));
    }

    @Test
    void httpFailureIsRethrownWithoutRequestUrlOrSecret() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.getForObject(anyString(), eq(WechatService.WechatResponse.class)))
                .thenThrow(new ResourceAccessException("I/O error on GET request for \"" + URL + "\": Read timed out"));

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> serviceWith(restTemplate).jscode2session(CODE));

        assertFalse(failure.getMessage().contains(SECRET), failure.getMessage());
        assertFalse(failure.getMessage().contains(CODE), failure.getMessage());
        assertFalse(failure.getMessage().contains("api.weixin.qq.com"), failure.getMessage());
    }

    @Test
    void platformErrorMessageIsNotPassedThroughButErrorCodeIs() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        WechatService.WechatResponse response = new WechatService.WechatResponse();
        response.setErrcode(40029);
        response.setErrmsg("invalid code, rid: " + SECRET);
        when(restTemplate.getForObject(anyString(), eq(WechatService.WechatResponse.class))).thenReturn(response);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> serviceWith(restTemplate).jscode2session(CODE));

        assertTrue(failure.getMessage().contains("40029"), failure.getMessage());
        assertFalse(failure.getMessage().contains(SECRET), failure.getMessage());
        assertFalse(failure.getMessage().contains("invalid code"), failure.getMessage());
    }
}
