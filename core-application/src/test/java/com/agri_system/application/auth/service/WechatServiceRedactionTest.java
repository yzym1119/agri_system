package com.agri_system.application.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private WechatService serviceWith(WechatApiClient api) {
        WechatService service = new WechatService(api);
        ReflectionTestUtils.setField(service, "appId", "wx1111111111111111");
        ReflectionTestUtils.setField(service, "appSecret", SECRET);
        return service;
    }

    @Test void incompleteSuccessfulResponseMustNotBecomeAnAuthenticatedIdentity() {
        var http=mock(WechatApiClient.class);var response=new ObjectMapper().createObjectNode().put("errcode",0);
        when(http.codeToSession(anyString(),anyString(),anyString())).thenReturn(response);
        assertThrows(IllegalStateException.class,()->serviceWith(http).jscode2session(CODE));
    }

    @Test
    void httpFailureIsRethrownWithoutRequestUrlOrSecret() {
        WechatApiClient restTemplate = mock(WechatApiClient.class);
        when(restTemplate.codeToSession(anyString(),anyString(),anyString()))
                .thenThrow(new ResourceAccessException("I/O error on GET request for \"" + URL + "\": Read timed out"));

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> serviceWith(restTemplate).jscode2session(CODE));

        assertFalse(failure.getMessage().contains(SECRET), failure.getMessage());
        assertFalse(failure.getMessage().contains(CODE), failure.getMessage());
        assertFalse(failure.getMessage().contains("api.weixin.qq.com"), failure.getMessage());
    }

    @Test
    void platformErrorMessageIsNotPassedThroughButErrorCodeIs() {
        WechatApiClient restTemplate = mock(WechatApiClient.class);
        var response = new ObjectMapper().createObjectNode().put("errcode",40029).put("errmsg","invalid code, rid: " + SECRET);
        when(restTemplate.codeToSession(anyString(),anyString(),anyString())).thenReturn(response);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> serviceWith(restTemplate).jscode2session(CODE));

        assertTrue(failure.getMessage().contains("40029"), failure.getMessage());
        assertFalse(failure.getMessage().contains(SECRET), failure.getMessage());
        assertFalse(failure.getMessage().contains("invalid code"), failure.getMessage());
    }
}
