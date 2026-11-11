package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.assistant.AgronomyAssistantWorkflow.Snapshot;
import com.agri_system.domain.agronomy.assistant.OfficialAgronomySourceRules;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;

/** 资格不是发布动作；调用方还须核真实会话/原问题归属及当前服务结果。 */
public final class AgronomyReferencePublicationPolicy {
    public static final String VERSION="OBSERVATION_QUOTES_V2";
    private final ObjectMapper json;
    public AgronomyReferencePublicationPolicy(ObjectMapper json) {
        this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(16000).maxNumberLength(24).build());
    }
    public boolean eligible(String raw,Snapshot original,Facts currentFacts,long currentFactsVersion,Instant at) {
        try {
            if(raw==null||raw.getBytes(StandardCharsets.UTF_8).length>24576||original==null||at==null
                    ||!original.facts().equals(currentFacts)||original.factsVersion()!=currentFactsVersion||!original.externalCallsPlanned())return false;
            var value=json.readTree(raw);
            if(value==null||!value.isObject()||!"REFERENCE".equals(value.path("kind").asText())||!VERSION.equals(value.path("policyVersion").asText())
                    ||!"OFFICIAL_PAGE".equals(value.path("evidenceOrigin").asText())||!value.path("verifiedDirectFetch").isBoolean()||!value.path("verifiedDirectFetch").booleanValue()
                    ||!value.path("published").isBoolean()||value.path("published").booleanValue())return false;
            var decision=new AgronomyAssistantSafetyPolicy().assess(original.question(),original.latestSupplement(),original.facts());
            String topic=value.path("topic").asText();if(!"REFERENCE".equals(decision.kind())||!topic.equals(decision.topic()))return false;
            if(!value.path("sources").isArray()||value.path("sources").isEmpty()||value.path("sources").size()>3
                    ||!value.path("citations").isArray()||value.path("citations").isEmpty()||value.path("citations").size()>3)return false;
            var sources=new HashMap<String,JsonNode>();
            var today=at.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            for(var source:value.path("sources")) {
                String id=text(source,"id"),excerpt=text(source,"excerpt"),title=text(source,"title");
                if(!id.matches("S[1-3]")||sources.putIfAbsent(id,source)!=null||!OfficialAgronomySourceRules.allowed(text(source,"url"))
                        ||title.isBlank()||title.length()>300||!safeText(title)||excerpt.isBlank()||excerpt.getBytes(StandardCharsets.UTF_8).length>4000
                        ||!safeText(excerpt)||!applies(excerpt,currentFacts)||!AgronomyAssistantSafetyPolicy.matchesTopic(excerpt,topic))return false;
                if(!text(source,"documentSha256").matches("[0-9a-f]{64}")||!text(source,"textSha256").matches("[0-9a-f]{64}")
                        ||!sha(excerpt).equals(text(source,"excerptSha256")))return false;
                Instant verified=Instant.parse(text(source,"verifiedAt")),until=Instant.parse(text(source,"validUntil"));
                LocalDate date=LocalDate.parse(text(source,"publishedOn"));
                if(verified.isAfter(at)||!until.equals(verified.plusSeconds(86400))||!at.isBefore(until)
                        ||date.isAfter(today)||date.isBefore(today.minusDays(365)))return false;
            }
            var used=new HashSet<String>();var pairs=new HashSet<String>();
            for(var citation:value.path("citations")) {
                String id=text(citation,"sourceId"),quote=text(citation,"quote");var source=sources.get(id);
                int length=quote.codePointCount(0,quote.length());
                if(citation.size()!=2||source==null||length<8||length>160||!text(source,"excerpt").contains(quote)||!applies(quote,currentFacts)
                        ||!safeText(quote)||!AgronomyAssistantSafetyPolicy.matchesTopic(quote,topic)||!pairs.add(id+"|"+quote))return false;
                used.add(id);
            }
            return used.equals(sources.keySet());
        }catch(Exception invalid){return false;}
    }
    static boolean safeText(String value) {
        return !AgronomyAssistantSafetyPolicy.privateText(value)&&!AgronomyAssistantSafetyPolicy.professional(value)
                &&!AgronomyAssistantSafetyPolicy.injection(value)&&!AgronomyAssistantSafetyPolicy.unsafeCharacters(value)&&!value.matches("(?s).*[<>].*");
    }
    private static boolean applies(String text,Facts f){return text.contains(f.region())&&text.contains(f.crop())&&text.contains(f.growthStage());}
    private static String text(JsonNode object,String key){var v=object.path(key);if(!v.isTextual())throw new IllegalArgumentException();return v.textValue();}
    private static String sha(String value) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
}
