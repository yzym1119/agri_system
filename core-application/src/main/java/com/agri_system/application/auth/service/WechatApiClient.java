package com.agri_system.application.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** 固定微信目的地，凭据不经过通用HTTP请求日志或原异常消息。 */
@Component
public class WechatApiClient {
    private final ObjectMapper json;
    private final HttpClient http;
    private final Duration timeout=Duration.ofSeconds(10);
    @Autowired public WechatApiClient(ObjectMapper json) {
        this(json,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    WechatApiClient(ObjectMapper json,HttpClient http){this.json=json;this.http=http;}
    public JsonNode accessToken(String appId,String secret){
        return send("/cgi-bin/token?grant_type=client_credential&appid="+encode(appId)+"&secret="+encode(secret),null);
    }
    public JsonNode codeToSession(String appId,String secret,String code){
        return send("/sns/jscode2session?appid="+encode(appId)+"&secret="+encode(secret)+"&js_code="+encode(code)+"&grant_type=authorization_code",null);
    }
    public JsonNode phoneNumber(String token,String code,String openId){
        return send("/wxa/business/getuserphonenumber?access_token="+encode(token),Map.of("code",code,"openid",openId));
    }
    public JsonNode subscription(String token,Map<String,Object> body){
        return send("/cgi-bin/message/subscribe/send?access_token="+encode(token),body);
    }
    private JsonNode send(String path,Map<String,Object> body){
        java.util.concurrent.CompletableFuture<HttpResponse<String>> pending=null;
        try{
            var builder=HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com"+path)).timeout(timeout);
            if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body),StandardCharsets.UTF_8));
            pending=http.sendAsync(builder.build(),ignored->new LimitedBodySubscriber());
            // HttpRequest.timeout不覆盖响应头后的完整正文读取；总截止必须等待整个body并取消超时请求。
            var response=pending.get(timeout.toNanos(),java.util.concurrent.TimeUnit.NANOSECONDS);
            if(response.statusCode()!=200||response.body()==null||response.body().length()>65536)throw new IllegalStateException();
            JsonNode data=json.readTree(response.body());if(data==null||!data.isObject())throw new IllegalStateException();return data;
        }catch(InterruptedException interrupted){if(pending!=null)pending.cancel(true);Thread.currentThread().interrupt();throw failure();}
        catch(Exception invalid){if(pending!=null)pending.cancel(true);throw failure();}
    }
    /** 到达字节上限立即取消上游，不能先无限缓冲正文再检查String长度。 */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final java.util.concurrent.CompletableFuture<String> result=new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        public java.util.concurrent.CompletionStage<String> getBody(){return result;}
        public void onSubscribe(java.util.concurrent.Flow.Subscription incoming){if(subscription!=null){incoming.cancel();return;}subscription=incoming;incoming.request(Long.MAX_VALUE);}
        public void onNext(java.util.List<java.nio.ByteBuffer> parts){
            if(result.isDone())return;
            for(var part:parts){
                if(part.remaining()>65536-bytes.size()){subscription.cancel();result.completeExceptionally(failure());return;}
                byte[] chunk=new byte[part.remaining()];part.get(chunk);bytes.writeBytes(chunk);
            }
        }
        public void onError(Throwable ignored){result.completeExceptionally(failure());}
        public void onComplete(){result.complete(bytes.toString(StandardCharsets.UTF_8));}
    }
    private static String encode(String value){if(value==null)throw failure();return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static IllegalStateException failure(){return new IllegalStateException("微信接口调用未完成，请稍后重试");}
}
