package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.assistant.*;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgronomyReferencePublicationPolicyTest {
    final ObjectMapper json=new ObjectMapper();
    final Instant at=Instant.parse("2026-11-11T03:00:00Z");
    final Facts facts=new Facts("黑龙江大庆","玉米","苗期");
    final AssistantBudgetPolicy budget=new AssistantBudgetPolicy(true,2,2,100000,"deepseek-flash",true,1024,300000,1200000,750,"TEST_PRICE");
    final AgronomyAssistantWorkflow.Snapshot original=new AgronomyAssistantWorkflow.Snapshot(1,1,2,1,"如何记录积水？",facts,"",budget.execution());
    final String quote="黑龙江大庆玉米苗期的积水记录包括日期与现场照片编号。";
    ObjectNode good() throws Exception {
        String hash=sha(quote);
        var source=Map.of("id","S1","title","软件观察资料","url","https://www.moa.gov.cn/test-fixture","publishedOn","2026-11-10",
                "verifiedAt",at.toString(),"validUntil",at.plusSeconds(86400).toString(),"documentSha256","0".repeat(64),"textSha256",hash,"excerpt",quote,"excerptSha256",hash);
        return (ObjectNode)json.valueToTree(Map.of("kind","REFERENCE","published",false,"publicationEligible",true,"policyVersion","OBSERVATION_QUOTES_V2","topic","积水观察记录",
                "evidenceOrigin","OFFICIAL_PAGE","verifiedDirectFetch",true,"sources",List.of(source),"citations",List.of(Map.of("sourceId","S1","quote",quote))));
    }
    static String sha(String text) throws Exception {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    boolean accepts(String raw,Facts current,long version,Instant now){return new AgronomyReferencePublicationPolicy(json).eligible(raw,original,current,version,now);}
    @Test void onlyCompleteCurrentOfficialEvidenceHasEligibilityAndDoesNotPublish() throws Exception {
        var draft=good();assertTrue(accepts(draft.toString(),facts,1,at));assertFalse(draft.path("published").asBoolean());
        assertFalse(accepts(draft.toString(),new Facts("其他地区","玉米","苗期"),1,at));assertFalse(accepts(draft.toString(),facts,2,at));
        assertFalse(accepts(draft.toString(),facts,1,at.plusSeconds(86401)));
    }
    @Test void booleanFlagsDoNotReplaceEvidenceAndLegacyCandidateIsNotPromoted() throws Exception {
        var draft=good();draft.remove("sources");assertFalse(accepts(draft.toString(),facts,1,at));
        draft=good();draft.put("evidenceOrigin","SEARCH_CANDIDATE");assertFalse(accepts(draft.toString(),facts,1,at));
        draft=good();draft.put("policyVersion","OBSERVATION_QUOTES_V1");assertFalse(accepts(draft.toString(),facts,1,at));
        draft=good();draft.put("published",true);assertFalse(accepts(draft.toString(),facts,1,at));
    }
    @Test void evidenceMutationOrWrongDestinationCannotPass() throws Exception {
        var draft=good();((ObjectNode)draft.path("sources").get(0)).put("excerpt",quote+"新内容");assertFalse(accepts(draft.toString(),facts,1,at));
        draft=good();((ObjectNode)draft.path("sources").get(0)).put("url","https://www.moa.gov.cn.evil.example/a");assertFalse(accepts(draft.toString(),facts,1,at));
        draft=good();((ObjectNode)draft.path("sources").get(0)).put("documentSha256","not-a-hash");assertFalse(accepts(draft.toString(),facts,1,at));
        draft=good();((ObjectNode)draft.path("sources").get(0)).put("validUntil",at.plusSeconds(86401).toString());assertFalse(accepts(draft.toString(),facts,1,at));
    }
    @Test void noInventedQuoteUnknownSourceOrDuplicateJson() throws Exception {
        var draft=good();((ObjectNode)draft.path("citations").get(0)).put("quote",quote+"自造建议");assertFalse(accepts(draft.toString(),facts,1,at));
        draft=good();((ObjectNode)draft.path("citations").get(0)).put("sourceId","S99");assertFalse(accepts(draft.toString(),facts,1,at));
        assertFalse(accepts(good().toString()+" {}",facts,1,at));
        assertFalse(accepts(good().toString().replace("\"REFERENCE\"","\"REFERENCE\",\"kind\":\"REFERENCE\""),facts,1,at));
    }
    @Test void undecodableReplacementCharacterDoesNotBecomePublishedEvidence() throws Exception {
        var draft=good();((ObjectNode)draft.path("sources").get(0)).put("title","软件资料\ufffd");
        assertFalse(accepts(draft.toString(),facts,1,at));
    }
}
