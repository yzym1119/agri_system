package com.agri_system.infrastructure.agronomy.assistant;

import com.agri_system.domain.agronomy.assistant.AgronomySourceFailure;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OfficialAgronomySourceGatewayTest {
    final Clock clock=Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"),ZoneOffset.UTC);
    final String url="https://www.moa.gov.cn/test-fixture/202611/t20261110_123456.htm";
    String html() {
        return "<html><head><meta charset='UTF-8'><meta name='ArticleTitle' content='软件观察资料'><meta name='PubDate' content='2026-11-10 10:00:00'><meta name='publishdate' content='2026-11-10'><meta name='Url' content='"+url+"'></head><body><nav>导航不作正文</nav><div class='TRS_Editor'><div class='TRS_Editor'><p>黑龙江大庆玉米苗期的积水记录包括日期&amp;现场照片编号。</p><p>第二段软件验证文本。</p><script>PRIVATE_SCRIPT</script><p hidden>HIDDEN_PRIVATE</p><p style='display: none'>CSS_HIDDEN</p><form>FORM_PRIVATE</form></div></div><footer>页脚不作正文</footer></body></html>";
    }
    @SuppressWarnings("unchecked") HttpClient transport(int status,String type,byte[] body) {
        var http=mock(HttpClient.class);var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);when(response.uri()).thenReturn(URI.create(url));when(response.body()).thenReturn(body);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type",List.of(type)),(k,v)->true));
        when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(response));return http;
    }
    HttpClient valid(){return transport(200,"text/html; charset=utf-8",html().getBytes(StandardCharsets.UTF_8));}
    @Test void disabledAndUnsafeDestinationsNeverSend() {
        var http=mock(HttpClient.class);var disabled=new OfficialAgronomySourceGateway(clock,false,http);
        assertEquals(AgronomySourceFailure.Reason.DISABLED,assertThrows(AgronomySourceFailure.class,()->disabled.fetch(url,Duration.ofSeconds(1))).reason());
        for(String value:List.of("http://www.moa.gov.cn/a","https://www.moa.gov.cn.evil.example/a","https://www.moa.gov.cn@127.0.0.1/a","https://127.0.0.1/a","file:///tmp/a","https://www.moa.gov.cn:8443/a","https://www.moa.gov.cn/a?q=private","https://www.moa.gov.cn/%2f%2fevil","https://www.moa.gov.cn/a#x"))
            assertThrows(AgronomySourceFailure.class,()->new OfficialAgronomySourceGateway(clock,true,http).fetch(value,Duration.ofSeconds(1)));
        verifyNoInteractions(http);
    }
    @Test void fixedAnonymousGetAndNestedArticleAreVerifiedWithoutSubresources() throws Exception {
        var http=valid();var page=new OfficialAgronomySourceGateway(clock,true,http).fetch(url,Duration.ofSeconds(10));
        var capture=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(1)).sendAsync(capture.capture(),any(HttpResponse.BodyHandler.class));
        var request=capture.getValue();assertEquals(URI.create(url),request.uri());assertEquals("GET",request.method());assertEquals(Duration.ofSeconds(5),request.timeout().orElseThrow());
        assertTrue(request.bodyPublisher().isEmpty());assertTrue(request.headers().firstValue("Authorization").isEmpty());assertTrue(request.headers().firstValue("Cookie").isEmpty());assertTrue(request.headers().firstValue("Referer").isEmpty());
        assertEquals(LocalDate.of(2026,11,10),page.publishedOn());assertEquals(clock.instant(),page.fetchedAt());assertEquals("软件观察资料",page.title());
        assertTrue(page.articleText().contains("日期&现场照片编号"));
        for(String excluded:List.of("导航","页脚","PRIVATE_SCRIPT","HIDDEN_PRIVATE","CSS_HIDDEN","FORM_PRIVATE"))assertFalse(page.articleText().contains(excluded));
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(page.articleText().getBytes(StandardCharsets.UTF_8))),page.textSha256());
        assertEquals(64,page.documentSha256().length());assertFalse(page.toString().contains("积水"));
    }
    @Test void missingConflictingOrBorrowedMetadataAndAmbiguousBodyAreRejected() {
        for(String value:List.of(
                html().replace("name='PubDate'","name='MissingDate'"),
                html().replace("2026-11-10 10:00:00","2026-02-30 10:00:00"),
                html().replace("2026-11-10 10:00:00","2026-11-11 23:00:00"),
                html().replace("name='publishdate' content='2026-11-10'","name='publishdate' content='2026-11-09'"),
                html().replace("</head>","<meta name='PubDate' content='2026-11-10'></head>"),
                html().replace("content='"+url+"'","content='https://www.moa.gov.cn/another-page'"),
                html().replace("TRS_Editor","UNKNOWN_CONTENT"),
                html().replace("<footer>","<div class='TRS_Editor'>第二个独立正文</div><footer>"))) {
            var gateway=new OfficialAgronomySourceGateway(clock,true,transport(200,"text/html",value.getBytes(StandardCharsets.UTF_8)));
            assertEquals(AgronomySourceFailure.Reason.CONTENT,assertThrows(AgronomySourceFailure.class,()->gateway.fetch(url,Duration.ofSeconds(1))).reason());
        }
    }
    @Test void redirectBadContentTypeInvalidUtf8AndOversizeDoNotBecomeVerifiedPages() {
        assertThrows(AgronomySourceFailure.class,()->new OfficialAgronomySourceGateway(clock,true,transport(302,"text/html",new byte[0])).fetch(url,Duration.ofSeconds(1)));
        for(String type:List.of("application/pdf","application/json","text/html; charset=GBK"))
            assertThrows(AgronomySourceFailure.class,()->new OfficialAgronomySourceGateway(clock,true,transport(200,type,html().getBytes(StandardCharsets.UTF_8))).fetch(url,Duration.ofSeconds(1)));
        assertThrows(AgronomySourceFailure.class,()->new OfficialAgronomySourceGateway(clock,true,transport(200,"text/html",new byte[]{(byte)0xc3,0x28})).fetch(url,Duration.ofSeconds(1)));
        assertThrows(AgronomySourceFailure.class,()->new OfficialAgronomySourceGateway(clock,true,transport(200,"text/html",new byte[262145])).fetch(url,Duration.ofSeconds(1)));
    }
    @Test void errorsNeverExposeUrlBodyOrOriginalException() {
        var http=mock(HttpClient.class);when(http.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("PRIVATE_BODY SECRET")));
        var error=assertThrows(AgronomySourceFailure.class,()->new OfficialAgronomySourceGateway(clock,true,http).fetch(url,Duration.ofSeconds(1)));
        assertNull(error.getCause());assertFalse(error.toString().contains("PRIVATE"));assertFalse(error.toString().contains(url));
    }
    @Test void htmlCharsetCannotContradictTheStrictUtf8Article() {
        String bad=html().replace("charset='UTF-8'","charset='GBK'");
        assertThrows(AgronomySourceFailure.class,()->new OfficialAgronomySourceGateway(clock,true,transport(200,"text/html",bad.getBytes(StandardCharsets.UTF_8))).fetch(url,Duration.ofSeconds(1)));
    }
    @Test void completeBodyDeadlineCancelsARealSlowResponse() throws Exception {
        try(var server=new LocalSourceServer(false)) {
            long start=System.nanoTime();
            var failure=assertThrows(AgronomySourceFailure.class,()->server.gateway().fetch(url,Duration.ofMillis(200)));
            assertTrue(Set.of(AgronomySourceFailure.Reason.DEADLINE,AgronomySourceFailure.Reason.NETWORK).contains(failure.reason()));
            assertTrue(Duration.ofNanos(System.nanoTime()-start).compareTo(Duration.ofSeconds(2))<0);
        }
    }
    @Test void actualStreamBeyondLimitIsRejectedBeforeCompleteBuffering() throws Exception {
        try(var server=new LocalSourceServer(true)) {
            var failure=assertThrows(AgronomySourceFailure.class,()->server.gateway().fetch(url,Duration.ofSeconds(2)));
            assertEquals(AgronomySourceFailure.Reason.CONTENT,failure.reason());
        }
    }
    @Test void realLocalHttpParsesTheCompleteBoundedArticle() throws Exception {
        try(var server=new LocalSourceServer(false,true)) {
            var page=server.gateway().fetch(url,Duration.ofSeconds(2));assertEquals(LocalDate.of(2026,11,10),page.publishedOn());assertTrue(page.articleText().contains("积水"));
        }
    }
    final class LocalSourceServer implements AutoCloseable {
        final com.sun.net.httpserver.HttpServer server;
        final java.util.concurrent.ExecutorService pool=Executors.newCachedThreadPool();
        LocalSourceServer(boolean overflow) throws Exception {this(overflow,false);}
        LocalSourceServer(boolean overflow,boolean happy) throws Exception {
            server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);server.setExecutor(pool);
            server.createContext("/",exchange->{
                exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");exchange.sendResponseHeaders(200,0);
                try(var body=exchange.getResponseBody()) {
                    if(happy)body.write(html().getBytes(StandardCharsets.UTF_8));
                    else if(overflow){byte[] block=new byte[8192];for(int n=0;n<10000;n++){body.write(block);body.flush();try{Thread.sleep(2);}catch(InterruptedException stopped){Thread.currentThread().interrupt();break;}}}
                    else {body.write('<');body.flush();try{Thread.sleep(2500);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}body.write('>');}
                }catch(java.io.IOException clientCancelled){ }
            });server.start();
        }
        @SuppressWarnings("unchecked") OfficialAgronomySourceGateway gateway() {
            var actual=HttpClient.newHttpClient();var forward=mock(HttpClient.class);
            when(forward.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{
                HttpRequest remote=call.getArgument(0);
                assertEquals(URI.create(url),remote.uri());
                var local=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/")).timeout(remote.timeout().orElseThrow()).GET().build();
                return actual.sendAsync(local,(HttpResponse.BodyHandler<byte[]>)call.getArgument(1)).thenApply(response->{
                    var mapped=mock(HttpResponse.class);when(mapped.statusCode()).thenReturn(response.statusCode());when(mapped.headers()).thenReturn(response.headers());when(mapped.body()).thenReturn(response.body());when(mapped.uri()).thenReturn(remote.uri());return mapped;
                });
            });
            return new OfficialAgronomySourceGateway(clock,true,forward);
        }
        public void close(){server.stop(0);pool.shutdownNow();}
    }
}
