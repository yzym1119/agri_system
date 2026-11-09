package com.agri_system.application.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatPhoneAuthorizationTest {
    static final String APP="wx1111111111111111",OPEN="IT_OPEN_ID",CODE="IT_PHONE_CODE",TOKEN="IT_ACCESS_TOKEN";
    final ObjectMapper json=new ObjectMapper();
    WechatAccessTokenService tokens(WechatApiClient api,StringRedisTemplate redis) {
        var tokens=new WechatAccessTokenService(redis,api);ReflectionTestUtils.setField(tokens,"appId",APP);ReflectionTestUtils.setField(tokens,"secret","IT_SECRET");return tokens;
    }
    String response(String phone,String country,String app,long timestamp) {
        return "{\"errcode\":0,\"phone_info\":{\"phoneNumber\":\""+phone+"\",\"purePhoneNumber\":\""+phone+"\",\"countryCode\":\""+country+"\",\"watermark\":{\"appid\":\""+app+"\",\"timestamp\":"+timestamp+"}}}";
    }
    @Test void actualOpenIdAndCodeAreBoundToProviderRequestAndOnlyValidatedPhoneBecomesEvidence() throws Exception {
        var api=mock(WechatApiClient.class);var access=mock(WechatAccessTokenService.class);when(access.requireAccessToken()).thenReturn(TOKEN);
        when(api.phoneNumber(TOKEN,CODE,OPEN)).thenReturn(json.readTree(response("13800000000","86",APP,Instant.now().getEpochSecond())));
        var evidence=new WechatPhoneAuthorizationService(api,access).authorize(APP,OPEN,CODE);
        assertEquals("13800000000",evidence.phone());assertEquals(APP,evidence.appId());assertEquals(OPEN,evidence.openId());
        assertFalse(evidence.toString().contains("13800000000"));
        verify(api).phoneNumber(TOKEN,CODE,OPEN);
    }
    @Test void malformedForeignOrStaleEvidenceNeverAuthorizesAndNeverEchoesProviderText() throws Exception {
        var api=mock(WechatApiClient.class);var access=mock(WechatAccessTokenService.class);when(access.requireAccessToken()).thenReturn(TOKEN);
        var service=new WechatPhoneAuthorizationService(api,access);long now=Instant.now().getEpochSecond();
        for(String body:new String[]{"null","{}","{\"errcode\":40029,\"errmsg\":\"IT_SECRET IT_PHONE_CODE\"}",response("13800000000","86",APP,now).replace("errcode\":0","errcode\":4294967296"),response("13800000000","86","wx2222222222222222",now),response("13800000000","1",APP,now),response("1","86",APP,now),response("13800000000","86",APP,now-1000),response("13800000000","86",APP,now+1000),response("13800000000","86",APP,now).replace("purePhoneNumber\":\"13800000000","purePhoneNumber\":\"13900000000")}) {
            when(api.phoneNumber(TOKEN,CODE,OPEN)).thenReturn(json.readTree(body));
            var failure=assertThrows(RuntimeException.class,()->service.authorize(APP,OPEN,CODE));assertFalse(failure.getMessage().contains("IT_SECRET"));assertFalse(failure.getMessage().contains(CODE));
        }
    }
    @Test void mainlandInternationalPrefixIsNormalizedOnlyWhenCountryAndPureNumberAgree() throws Exception {
        var api=mock(WechatApiClient.class);var access=mock(WechatAccessTokenService.class);when(access.requireAccessToken()).thenReturn(TOKEN);
        var service=new WechatPhoneAuthorizationService(api,access);
        for(String full:new String[]{"8613800000000","+8613800000000"}){
            var node=json.readTree(response("13800000000","86",APP,Instant.now().getEpochSecond()));((com.fasterxml.jackson.databind.node.ObjectNode)node.path("phone_info")).put("phoneNumber",full);
            when(api.phoneNumber(TOKEN,CODE,OPEN)).thenReturn(node);assertEquals("13800000000",service.authorize(APP,OPEN,CODE).phone());
        }
    }
    @Test void invalidAccessTokenDoesNotAutomaticallyReplayOneTimePhoneCode() throws Exception {
        var api=mock(WechatApiClient.class);var access=mock(WechatAccessTokenService.class);when(access.requireAccessToken()).thenReturn(TOKEN);
        when(api.phoneNumber(TOKEN,CODE,OPEN)).thenReturn(json.readTree("{\"errcode\":40001}"));
        assertThrows(RuntimeException.class,()->new WechatPhoneAuthorizationService(api,access).authorize(APP,OPEN,CODE));
        verify(api,times(1)).phoneNumber(TOKEN,CODE,OPEN);verify(access).invalidate();
    }
    @SuppressWarnings("unchecked") @Test void accessTokenUsesExistingAppScopedCacheAndNeverInventsExpiry() throws Exception {
        var api=mock(WechatApiClient.class);var redis=mock(StringRedisTemplate.class);var values=mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);
        var service=tokens(api,redis);when(api.accessToken(APP,"IT_SECRET")).thenReturn(json.readTree("{\"access_token\":\"IT_ACCESS_TOKEN\",\"expires_in\":7200}"));
        assertEquals(TOKEN,service.requireAccessToken());verify(values).set("wechat:access_token:"+APP,TOKEN,Duration.ofSeconds(7000));
        when(values.get("wechat:access_token:"+APP)).thenReturn("IT_CACHED");assertEquals("IT_CACHED",service.requireAccessToken());verify(api,times(1)).accessToken(APP,"IT_SECRET");
        when(values.get(anyString())).thenReturn(null);
        for(String body:new String[]{"null","{}","{\"access_token\":\"IT_NEW\"}","{\"access_token\":\"IT_NEW\",\"expires_in\":0}","{\"errcode\":1,\"errmsg\":\"IT_SECRET\",\"access_token\":\"IT_NEW\",\"expires_in\":7200}"}) {
            when(api.accessToken(APP,"IT_SECRET")).thenReturn(json.readTree(body));assertThrows(RuntimeException.class,service::requireAccessToken);
        }
        verify(values,never()).set(anyString(),eq("IT_NEW"),any(Duration.class));
    }
}
