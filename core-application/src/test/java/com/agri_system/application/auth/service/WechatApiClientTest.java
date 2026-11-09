package com.agri_system.application.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WechatApiClientTest {
    @SuppressWarnings("unchecked") @Test void phoneHttpHasFixedDestinationTimeoutAndActualServerOpenId() throws Exception {
        var http=mock(HttpClient.class);var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"errcode\":0}");
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));
        new WechatApiClient(new ObjectMapper(),http).phoneNumber("IT_TOKEN&x=1","IT_CODE","IT_OPEN");
        var capture=ArgumentCaptor.forClass(HttpRequest.class);verify(http).sendAsync(capture.capture(),any(HttpResponse.BodyHandler.class));var request=capture.getValue();
        assertEquals("https",request.uri().getScheme());assertEquals("api.weixin.qq.com",request.uri().getHost());assertEquals("/wxa/business/getuserphonenumber",request.uri().getPath());
        assertEquals("POST",request.method());assertEquals(java.time.Duration.ofSeconds(10),request.timeout().orElseThrow());assertTrue(request.uri().getRawQuery().contains("%26"));
        var bytes=new java.io.ByteArrayOutputStream();request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription s){s.request(Long.MAX_VALUE);}public void onNext(ByteBuffer data){while(data.hasRemaining())bytes.write(data.get());}public void onError(Throwable t){fail(t);}public void onComplete(){}
        });
        var body=new ObjectMapper().readTree(bytes.toByteArray());assertEquals("IT_CODE",body.path("code").asText());assertEquals("IT_OPEN",body.path("openid").asText());
    }
    @SuppressWarnings("unchecked") @Test void errorsAndInvalidJsonCannotExposeCredentialUrlsOrProviderBody() throws Exception {
        var http=mock(HttpClient.class);var response=mock(HttpResponse.class);var client=new WechatApiClient(new ObjectMapper(),http);
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("https://api.weixin.qq.com?access_token=IT_SECRET IT_CODE")));
        var failure=assertThrows(RuntimeException.class,()->client.phoneNumber("IT_SECRET","IT_CODE","IT_OPEN"));assertNull(failure.getCause());assertFalse(failure.toString().contains("IT_SECRET"));
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));when(response.statusCode()).thenReturn(503);when(response.body()).thenReturn("IT_SECRET");
        assertFalse(assertThrows(RuntimeException.class,()->client.accessToken("wx1111111111111111","IT_SECRET")).toString().contains("IT_SECRET"));
        when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("IT_SECRET invalid json");
        assertFalse(assertThrows(RuntimeException.class,()->client.phoneNumber("IT_SECRET","IT_CODE","IT_OPEN")).toString().contains("IT_SECRET"));
    }
    @Test void authenticationDtoDiagnosticStringsNeverContainCredentials() {
        var command=new com.agri_system.application.auth.dto.ClientWechatLoginCommand();command.setCode("IT_PRIVATE_CODE");
        var login=new com.agri_system.application.auth.dto.LoginResultVO();login.setTokenValue("IT_PRIVATE_TOKEN");
        var reply=new ClientOnboardingService.Reply("PHONE_REQUIRED",login,"Authorization","IT_PRIVATE_TOKEN",1L,null);
        assertFalse(command.toString().contains("IT_PRIVATE_CODE"));assertFalse(login.toString().contains("IT_PRIVATE_TOKEN"));assertFalse(reply.toString().contains("IT_PRIVATE_TOKEN"));
    }

    @Test void totalDeadlineIncludesResponseBodyAfterHeadersHaveAlreadyArrived() throws Exception {
        try(var local=new LocalWechatServer(false)){
            var client=local.client();org.springframework.test.util.ReflectionTestUtils.setField(client,"timeout",java.time.Duration.ofMillis(200));
            long start=System.nanoTime();assertThrows(RuntimeException.class,()->client.phoneNumber("IT_TOKEN","IT_CODE","IT_OPEN"));
            assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000,"响应体也必须受总截止限制");
        }
    }
    @Test void oversizedStreamIsRejectedBeforeWaitingForItsEnd() throws Exception {
        try(var local=new LocalWechatServer(true)){
            var client=local.client();long start=System.nanoTime();assertThrows(RuntimeException.class,()->client.phoneNumber("IT_TOKEN","IT_CODE","IT_OPEN"));
            assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000,"超限时必须停止接收，不能等正文全缓冲");
        }
    }
    /** 只在测试中将固定微信请求交给真实本机JDK传输，不更改生产目的地。 */
    static class LocalWechatServer implements AutoCloseable {
        final com.sun.net.httpserver.HttpServer server;final java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newCachedThreadPool();
        LocalWechatServer(boolean large) throws Exception {
            server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);server.setExecutor(workers);
            server.createContext("/",exchange->{
                try(var output=exchange.getResponseBody()){
                    exchange.sendResponseHeaders(200,0);
                    output.write((large?"{\"padding\":\""+"A".repeat(70000):"{\"errcode\":0").getBytes(java.nio.charset.StandardCharsets.UTF_8));output.flush();
                    try{Thread.sleep(1500);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                    output.write((large?"\"}":"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }catch(java.io.IOException cancelled){}finally{exchange.close();}
            });server.start();
        }
        HttpRequest local(HttpRequest source){
            var builder=HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/")).timeout(source.timeout().orElseThrow());
            return builder.method(source.method(),source.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build();
        }
        @SuppressWarnings({"unchecked","rawtypes"}) WechatApiClient client() throws Exception {
            var transport=HttpClient.newHttpClient();var forward=mock(HttpClient.class);
            when(forward.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->transport.send(local(call.getArgument(0)),(HttpResponse.BodyHandler)call.getArgument(1)));
            when(forward.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->transport.sendAsync(local(call.getArgument(0)),(HttpResponse.BodyHandler)call.getArgument(1)));
            return new WechatApiClient(new ObjectMapper(),forward);
        }
        public void close(){server.stop(0);workers.shutdownNow();}
    }

}
