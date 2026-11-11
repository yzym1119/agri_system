package com.agri_system.infrastructure.agronomy.assistant;

import com.agri_system.domain.agronomy.assistant.AgronomySourceGateway;
import com.agri_system.domain.agronomy.assistant.AgronomySourceFailure;
import com.agri_system.domain.agronomy.assistant.AgronomySourceFailure.Reason;
import com.agri_system.domain.agronomy.assistant.OfficialAgronomySourceRules;
import com.agri_system.infrastructure.common.http.BoundedResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.http.MediaType;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 匿名、只读、固定官方主机的静态原页核验；默认关闭，不注册Bean。 */
public final class OfficialAgronomySourceGateway implements AgronomySourceGateway {
    private static final int MAX_BYTES=262144;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private final Clock clock;
    private final boolean enabled;
    private final HttpClient http;
    public OfficialAgronomySourceGateway(Clock clock){this(clock,false);}
    public OfficialAgronomySourceGateway(Clock clock,boolean enabled) {
        this(clock,enabled,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    OfficialAgronomySourceGateway(Clock clock,boolean enabled,HttpClient http){this.clock=clock;this.enabled=enabled;this.http=http;}
    @Override public Page fetch(String value,Duration maximum) {
        if(!enabled)throw failure(Reason.DISABLED);
        var uri=OfficialAgronomySourceRules.require(value);
        if(maximum==null||maximum.isNegative()||maximum.isZero())throw failure(Reason.INPUT);
        Duration timeout=maximum.compareTo(Duration.ofSeconds(5))>0?Duration.ofSeconds(5):maximum;
        long deadline=System.nanoTime()+timeout.toNanos();
        var subscriber=new BoundedResponseBody(MAX_BYTES,()->failure(Reason.CONTENT),()->failure(Reason.NETWORK));
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            var request=HttpRequest.newBuilder(uri).timeout(timeout).header("Accept","text/html")
                    .header("Accept-Encoding","identity").header("User-Agent","AgriSystem-SourceVerifier/1.0").GET().build();
            pending=http.sendAsync(request,ignored->subscriber);
            var response=pending.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(response.statusCode()!=200)throw failure(Reason.HTTP);
            if(!uri.equals(response.uri()))throw failure(Reason.CONTENT);
            var media=MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""));
            if(!"text".equalsIgnoreCase(media.getType())||!"html".equalsIgnoreCase(media.getSubtype())
                    ||media.getCharset()!=null&&!StandardCharsets.UTF_8.equals(media.getCharset())
                    ||!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity"))throw failure(Reason.CONTENT);
            byte[] raw=response.body();if(raw==null||raw.length==0||raw.length>MAX_BYTES)throw failure(Reason.CONTENT);
            String html=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
            var page=parse(uri.toString(),html,raw);
            if(System.nanoTime()>deadline)throw failure(Reason.DEADLINE);
            return page;
        } catch(InterruptedException interrupted){abort(pending,subscriber);Thread.currentThread().interrupt();throw failure(Reason.NETWORK);}
        catch(TimeoutException timeoutFailure){abort(pending,subscriber);throw failure(Reason.DEADLINE);}
        catch(ExecutionException invalid){abort(pending,subscriber);if(invalid.getCause() instanceof AgronomySourceFailure own)throw own;throw failure(Reason.NETWORK);}
        catch(AgronomySourceFailure own){abort(pending,subscriber);throw own;}
        catch(Exception invalid){abort(pending,subscriber);throw failure(Reason.CONTENT);}
    }
    private Page parse(String url,String html,byte[] raw) throws Exception {
        var document=Jsoup.parse(html,url);
        for(var declaration:document.head().select("meta[charset]"))
            if(!StandardCharsets.UTF_8.equals(java.nio.charset.Charset.forName(declaration.attr("charset").trim())))throw failure(Reason.CONTENT);
        for(var declaration:document.head().select("meta[http-equiv]")) {
            if(!declaration.attr("http-equiv").equalsIgnoreCase("Content-Type"))continue;
            var media=MediaType.parseMediaType(declaration.attr("content"));
            if(!"text".equalsIgnoreCase(media.getType())||!"html".equalsIgnoreCase(media.getSubtype())
                    ||media.getCharset()!=null&&!StandardCharsets.UTF_8.equals(media.getCharset()))throw failure(Reason.CONTENT);
        }
        String title=meta(document.head(),"ArticleTitle",true);
        if(title.length()>300||title.isBlank()||title.codePoints().anyMatch(Character::isISOControl))throw failure(Reason.CONTENT);
        String originalUrl=meta(document.head(),"Url",true);
        if(!OfficialAgronomySourceRules.require(originalUrl).toString().equals(url))throw failure(Reason.CONTENT);
        LocalDate published=date(meta(document.head(),"PubDate",true));
        String alternate=meta(document.head(),"publishdate",false);
        if(alternate!=null&&!date(alternate).equals(published))throw failure(Reason.CONTENT);
        document.select("script,style,noscript,iframe,object,embed,svg,form,template,[hidden],[aria-hidden=true]").remove();
        for(var element:document.select("[style]")) {
            String style=element.attr("style").replaceAll("\\s+","").toLowerCase(java.util.Locale.ROOT);
            if(style.contains("display:none")||style.contains("visibility:hidden"))element.remove();
        }
        var roots=document.select("div.TRS_Editor").stream().filter(e->e.parents().stream().noneMatch(p->p.hasClass("TRS_Editor"))).toList();
        if(roots.size()!=1)throw failure(Reason.CONTENT);
        String text=articleText(roots.get(0));
        if(text.isBlank()||text.length()>32000)throw failure(Reason.CONTENT);
        return new Page(url,title,text,published,clock.instant(),sha(raw),sha(text.getBytes(StandardCharsets.UTF_8)));
    }
    private static String meta(Element head,String name,boolean required) {
        var values=head.select("meta[name]").stream().filter(m->m.attr("name").equalsIgnoreCase(name)).map(m->m.attr("content").trim()).toList();
        if(values.size()>1||required&&(values.size()!=1||values.get(0).isBlank()))throw failure(Reason.CONTENT);
        return values.isEmpty()?null:values.get(0);
    }
    private LocalDate date(String value) {
        try {
            if(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                var date=LocalDate.parse(value);if(date.isAfter(clock.instant().atZone(ZONE).toLocalDate()))throw failure(Reason.CONTENT);return date;
            }
            if(!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}(:[0-9]{2})?"))throw failure(Reason.CONTENT);
            var at=LocalDateTime.parse(value.replace(' ','T'));
            if(at.atZone(ZONE).toInstant().isAfter(clock.instant()))throw failure(Reason.CONTENT);return at.toLocalDate();
        }catch(RuntimeException invalid){throw failure(Reason.CONTENT);}
    }
    private static String articleText(Element root) {
        var output=new StringBuilder();var blocks=Set.of("div","p","li","tr","td","th","h1","h2","h3","h4","h5","h6","br");
        NodeTraversor.traverse(new NodeVisitor() {
            public void head(Node node,int depth) {if(node instanceof TextNode text)output.append(text.getWholeText());else if(node instanceof Element e&&blocks.contains(e.normalName()))output.append('\n');}
            public void tail(Node node,int depth) {if(node instanceof Element e&&blocks.contains(e.normalName()))output.append('\n');}
        },root);
        return output.toString().replace('\u00a0',' ').lines().map(s->s.replaceAll("[ \\t\\u000B\\f\\r]+"," ").trim()).filter(s->!s.isEmpty()).collect(java.util.stream.Collectors.joining("\n"));
    }
    private static String sha(byte[] bytes) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void abort(CompletableFuture<?> pending,BoundedResponseBody subscriber){if(pending!=null)pending.cancel(true);subscriber.cancel();}
    private static AgronomySourceFailure failure(Reason reason){return new AgronomySourceFailure(reason);}
}
