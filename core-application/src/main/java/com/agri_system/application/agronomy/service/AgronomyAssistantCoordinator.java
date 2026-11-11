package com.agri_system.application.agronomy.service;

import com.agri_system.domain.agronomy.assistant.AssistantBudgetPolicy;
import com.agri_system.application.auth.service.AuthService;
import com.agri_system.domain.auth.model.ClientAuthorization;
import com.agri_system.domain.agronomy.assistant.AssistantRequestMetadata;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantRepository;
import com.agri_system.domain.agronomy.assistant.AgronomyAssistantWorkflow;
import com.agri_system.domain.agronomy.assistant.AssistantCallLedger;
import com.agri_system.domain.agronomy.model.AgronomyCase;
import com.agri_system.domain.agronomy.model.AgronomyCaseEntry;
import com.agri_system.domain.agronomy.repository.AgronomyCaseRepository;
import com.agri_system.domain.customer.repository.CustomerRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.EnumMap;
import java.math.BigInteger;
import java.util.function.Supplier;

/** 持久许可及保守预占；未注册Bean/API，后续可信发布工作流实现后才接线。 */
public final class AgronomyAssistantCoordinator {
    private final AgronomyCaseRepository cases;
    private final AgronomyAssistantRepository requests;
    private final StorePublicationRepository stores;
    private final CustomerRepository customers;
    private final AuthService authService;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Clock clock;
    private final AssistantBudgetPolicy policy;
    private final AgronomyAssistantWorkflow workflow;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public record Command(String requestKey,String expectedVersion,String factsVersion,boolean shareContextConfirmed) {}
    /** 只给出处理状态，未发布草案和私有执行凭证不出现在结果视图。 */
    public record View(String requestKey,String caseId,String state,String queuedVersion,String factsVersion,String outcomeKind,String handoffStatus) {}
    private record Original(AgronomyCaseEntry entry,AssistantRequestMetadata metadata,AgronomyAssistantWorkflow.Snapshot snapshot) {}
    private record Dispatch(Original original,String claimKey) {}

    public AgronomyAssistantCoordinator(AgronomyCaseRepository cases,AgronomyAssistantRepository requests,
            StorePublicationRepository stores,CustomerRepository customers,AuthService authService,PlatformTransactionManager manager,
            ObjectMapper json,Clock clock,AssistantBudgetPolicy policy,AgronomyAssistantWorkflow workflow) {
        this.cases=cases;this.requests=requests;this.stores=stores;this.customers=customers;this.authService=Objects.requireNonNull(authService);this.clock=clock;this.policy=policy;this.workflow=workflow;
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).maxStringLength(49152).maxNumberLength(24).build());
    }
    public View submit(ClientAuthorization authorization,Long caseId,Command command) {
        outside();validate(command);auth(authorization);
        long customer=authorization.customerId();
        Original reserved=inTx(()->reserve(authorization,caseId,command));
        if(!"QUEUED".equals(reserved.metadata().state()))return view(reserved);
        Dispatch dispatch=inTx(()->claim(authorization,caseId,command));
        if(dispatch==null)return read(authorization,caseId,command.requestKey());
        String staged;
        try { outside();staged=workflow.stage(dispatch.original().snapshot(),ledger(dispatch));validateStaged(staged,dispatch.original().snapshot()); }
        catch(AssistantCallLedger.Stopped stopped) { return inTx(()->record(dispatch,"COMPLETED","STALE",null)); }
        catch(RuntimeException failure) { return inTx(()->record(dispatch,"UNKNOWN","UNKNOWN",null)); }
        String output=staged;
        return inTx(()->{
            var row=cases.findOwned(caseId,customer,true).orElseThrow(AgronomyAssistantCoordinator::unavailable);
            boolean fresh=validAuthorization(dispatch.original().snapshot().authorization())&&fresh(row,dispatch.original().snapshot())&&activeStore(row.storeId());
            return record(dispatch,"COMPLETED",fresh?"STAGED":"STALE",fresh?output:null);
        });
    }
    public View read(ClientAuthorization authorization,Long caseId,String key) {
        outside();auth(authorization);positive(caseId);uuid(key);
        long customer=authorization.customerId();
        cases.findOwned(caseId,customer,false).orElseThrow(AgronomyAssistantCoordinator::unavailable);
        return view(original(customer,caseId,key,null));
    }
    /** 仅标记久未决状态，不重新调用供应商、不释放最大预占。 */
    public View markUnknown(ClientAuthorization authorization,Long caseId,String key) {
        outside();auth(authorization);positive(caseId);uuid(key);
        long customer=authorization.customerId();
        return inTx(()->{
            cases.findOwned(caseId,customer,true).orElseThrow(AgronomyAssistantCoordinator::unavailable);
            var o=original(customer,caseId,key,null);
            if("RUNNING".equals(o.metadata().state())&&!o.metadata().updatedAt().plusSeconds(90).isAfter(now()))
                requests.finish(o.entry().id(),o.metadata().claimKey(),"UNKNOWN",encode(java.util.Map.of("kind","UNKNOWN")),now());
            return view(original(customer,caseId,key,null));
        });
    }
    private Original reserve(ClientAuthorization authorization,Long caseId,Command command) {
        long customer=authorization.customerId(),authVersion=authorization.customerAuthVersion();
        var row=cases.findOwned(caseId,customer,true).orElseThrow(AgronomyAssistantCoordinator::unavailable);
        if(cases.findRequest("CUSTOMER",customer,command.requestKey()).isPresent())return original(customer,caseId,command.requestKey(),command);
        policy.requireEnabled();requireStore(row.storeId());auth(authorization);
        if(row.version()!=number(command.expectedVersion())||row.factsVersion()!=number(command.factsVersion())||row.version()==Long.MAX_VALUE
                ||row.factsVersion()<1||row.factsVersion()>row.version()||!Set.of("COLLECTING","WAITING_REVIEW","ACTIVE").contains(row.status()))throw invalid("问题情况已变化，请重新核对");
        if(requests.hasOpen(caseId))throw invalid("此问题还有原AI请求待核对，不能另号调用");
        String supplement=cases.latestCustomerSupplement(caseId,row.version()).map(AgronomyCaseEntry::body).orElse("");
        boolean external=policy.searchRequired()&&"REFERENCE".equals(new AgronomyAssistantSafetyPolicy().assess(row.question(),supplement,row.facts()).kind());
        long model=external?policy.modelReservation():0,search=external?policy.searchMicrousd():0;
        // 同一店锁下只取一次业务日期，不能把旧日核过的额度写入新日。
        LocalDateTime reservationAt=now();LocalDate budgetDay=reservationAt.toLocalDate();
        var used=requests.usage(row.storeId(),budgetDay);
        try {if(external&&(used.modelCalls()>=policy.dailyModelCalls()||used.searchCalls()>=policy.dailySearchCalls()
                    ||Math.addExact(used.reservedMicrousd(),Math.addExact(model,search))>policy.dailyMicrousd()))throw invalid("本店今日AI调用额度不足，请联系门店");}
        catch(ArithmeticException overflow){throw invalid("AI预占合计超出支持范围");}
        var snapshot=new AgronomyAssistantWorkflow.Snapshot(authVersion,row.version(),row.version()+1,row.factsVersion(),row.question(),row.facts(),supplement,policy.execution(),external?"EXTERNAL":"LOCAL",authorization);
        String saved=encode(snapshot);if(saved.getBytes(StandardCharsets.UTF_8).length>49152)throw invalid("AI上下文超出支持范围");
        var entry=new AgronomyCaseEntry(null,caseId,"CUSTOMER",customer,command.requestKey(),hash(caseId,command,saved),"ASSISTANT_REQUEST","请求AI说明（尚未发布）","",row.facts(),null,row.version(),row.version()+1,row.factsVersion(),row.status(),reservationAt);
        if(!cases.insertEntry(entry))throw invalid("原AI请求身份已被占用，请核对原号");
        var inserted=cases.findRequest("CUSTOMER",customer,command.requestKey()).orElseThrow(AgronomyAssistantCoordinator::unavailable);
        requests.initialize(new AssistantRequestMetadata(inserted.id(),"QUEUED",saved,null,budgetDay,model,search,null,reservationAt));
        if(!cases.update(caseId,row.version(),row.facts(),row.status(),row.factsVersion(),row.assignedStaffId(),reservationAt))throw unavailable();
        return original(customer,caseId,command.requestKey(),command);
    }
    private Dispatch claim(ClientAuthorization authorization,Long caseId,Command command) {
        long customer=authorization.customerId();
        var row=cases.findOwned(caseId,customer,true).orElseThrow(AgronomyAssistantCoordinator::unavailable);
        var o=original(customer,caseId,command.requestKey(),command);
        if(!"QUEUED".equals(o.metadata().state()))return null;
        boolean storeActive=stores.find(row.storeId(),true).filter(s->Integer.valueOf(1).equals(s.status())&&Integer.valueOf(0).equals(s.deleted())).isPresent();
        if(!storeActive||!Objects.equals(authorization,o.snapshot().authorization())||!validAuthorization(o.snapshot().authorization())||!fresh(row,o.snapshot())
                ||!policy.enabled()||!policy.execution().equals(o.snapshot().execution())||!day().equals(o.metadata().budgetDay())) {
            if(!requests.failBeforeDispatch(o.entry().id(),encode(java.util.Map.of("kind","NOT_DISPATCHED")),now()))throw unavailable();
            return null;
        }
        String key=UUID.randomUUID().toString();
        if(!requests.claim(o.entry().id(),key,now()))return null;
        return new Dispatch(original(customer,caseId,command.requestKey(),command),key);
    }
    private View record(Dispatch d,String state,String kind,String staged) {
        // 不覆盖entry的原body/hash/版本收据。仅同一次私有执行凭证可提交处理结果。
        var e=d.original().entry();
        var row=cases.find(e.caseId(),true).orElseThrow(AgronomyAssistantCoordinator::unavailable);
        var outcomeFields=new java.util.LinkedHashMap<String,Object>();outcomeFields.put("kind",kind);
        if(staged!=null) {
            var draft=parseTree(staged);outcomeFields.put("draft",draft);
            if("COMPLETED".equals(state)&&"STAGED".equals(kind)&&"HUMAN".equals(draft.path("kind").asText())) {
                long handoffVersion=row.version();
                if(!"WAITING_REVIEW".equals(row.status())) {
                    if(row.version()==Long.MAX_VALUE||!cases.update(row.id(),row.version(),row.facts(),"WAITING_REVIEW",row.factsVersion(),row.assignedStaffId(),now()))throw unavailable();
                    handoffVersion++;
                }
                // 原AI请求的处理结果就是转交事实，不伪造客户消息或人工答复，也不改原收据。
                outcomeFields.put("handoffStatus","WAITING_REVIEW");outcomeFields.put("handoffVersion",Long.toString(handoffVersion));
            }
        }
        String outcome=encode(outcomeFields);
        if(!requests.finish(e.id(),d.claimKey(),state,outcome,now()))throw unavailable();
        return view(original(e.actorId(),e.caseId(),e.requestKey(),null));
    }
    private record Step(String state,Long inputTokens,Long outputTokens,Long configuredCostMicrousd) {}
    private AssistantCallLedger ledger(Dispatch d) {
        return new AssistantCallLedger() {
            private int sourceReads;
            public void begin(Stage stage) { changeStep(d,stage,"STARTED",0,0); }
            public void succeeded(Stage stage,long input,long output) { changeStep(d,stage,"SUCCEEDED",input,output); }
            public void unknown(Stage stage) { changeStep(d,stage,"UNKNOWN",0,0); }
            public synchronized void beforeSourceRead() {
                outside();if(sourceReads>=3)throw new Stopped();
                inTx(()->{
                    var entry=d.original().entry();var s=d.original().snapshot();
                    var row=cases.find(entry.caseId(),true).orElseThrow(AgronomyAssistantCoordinator::unavailable);
                    var m=requests.find(entry.id()).orElseThrow(AgronomyAssistantCoordinator::unavailable);
                    if(!d.claimKey().equals(m.claimKey())||!ready(row,m,s,entry.actorId()))throw new Stopped();
                    var steps=decodeSteps(requests.steps(entry.id()).orElse(null),s,m);
                    if(!steps.containsKey(Stage.SEARCH)||!"SUCCEEDED".equals(steps.get(Stage.SEARCH).state())||steps.containsKey(Stage.MODEL))throw new Stopped();
                    return null;
                });
                sourceReads++;
            }
        };
    }
    private boolean ready(AgronomyCase row,AssistantRequestMetadata m,AgronomyAssistantWorkflow.Snapshot s,Long customer) {
        return s.externalCallsPlanned()&&"RUNNING".equals(m.state())&&fresh(row,s)&&validAuthorization(s.authorization())&&s.authorization().customerId()==customer
                &&activeStore(row.storeId())&&policy.enabled()&&policy.execution().equals(s.execution())&&day().equals(m.budgetDay());
    }
    private void changeStep(Dispatch d,AssistantCallLedger.Stage stage,String state,long input,long output) {
        outside();if(stage==null)throw unavailable();
        inTx(()->{
            var entry=d.original().entry();var snapshot=d.original().snapshot();
            var row=cases.find(entry.caseId(),true).orElseThrow(AgronomyAssistantCoordinator::unavailable);
            var m=requests.find(entry.id()).orElseThrow(AgronomyAssistantCoordinator::unavailable);
            if(!d.claimKey().equals(m.claimKey())||!Set.of("RUNNING","UNKNOWN").contains(m.state()))throw unavailable();
            String previous=requests.steps(entry.id()).orElse(null);
            var steps=decodeSteps(previous,snapshot,m);
            Long cost=null;Long in=null;Long out=null;
            if("STARTED".equals(state)) {
                if(!ready(row,m,snapshot,entry.actorId()))throw new AssistantCallLedger.Stopped();
                if(steps.containsKey(stage)||stage==AssistantCallLedger.Stage.SEARCH&&(!snapshot.execution().searchRequired()||!steps.isEmpty())
                        ||stage==AssistantCallLedger.Stage.MODEL&&snapshot.execution().searchRequired()
                        &&(!steps.containsKey(AssistantCallLedger.Stage.SEARCH)||!"SUCCEEDED".equals(steps.get(AssistantCallLedger.Stage.SEARCH).state())))throw unavailable();
            } else {
                if(!steps.containsKey(stage)||!"STARTED".equals(steps.get(stage).state()))throw unavailable();
                if("SUCCEEDED".equals(state)) {
                    if(stage==AssistantCallLedger.Stage.MODEL) {
                        in=input;out=output;
                        cost=modelCost(snapshot.execution(),input,output);
                        if(cost>m.modelReservedMicrousd())throw unavailable();
                    } else {
                        if(input!=0||output!=0)throw unavailable();
                        cost=snapshot.execution().searchMicrousd();
                    }
                } else if(!"UNKNOWN".equals(state))throw unavailable();
            }
            steps.put(stage,new Step(state,in,out,cost));String next=encode(steps);
            if(next.getBytes(StandardCharsets.UTF_8).length>8192||!requests.compareSteps(entry.id(),d.claimKey(),previous,next,now()))throw unavailable();
            return null;
        });
    }
    private static long modelCost(AssistantBudgetPolicy.Execution execution,long input,long output) {
        if(input<1||input>32768||output<1||output>execution.maxOutputTokens())throw unavailable();
        var million=BigInteger.valueOf(1000000);
        return BigInteger.valueOf(input).multiply(BigInteger.valueOf(execution.inputMicrousdPerMillion()))
                .add(BigInteger.valueOf(output).multiply(BigInteger.valueOf(execution.outputMicrousdPerMillion())))
                .add(million.subtract(BigInteger.ONE)).divide(million).longValueExact();
    }
    private EnumMap<AssistantCallLedger.Stage,Step> decodeSteps(String value,AgronomyAssistantWorkflow.Snapshot snapshot,AssistantRequestMetadata metadata) {
        var out=new EnumMap<AssistantCallLedger.Stage,Step>(AssistantCallLedger.Stage.class);if(value==null)return out;
        try {
            if(value.getBytes(StandardCharsets.UTF_8).length>8192)throw unavailable();
            var root=parseTree(value);var fields=root.fields();
            while(fields.hasNext()) {
                var field=fields.next();var stage=AssistantCallLedger.Stage.valueOf(field.getKey());var step=json.treeToValue(field.getValue(),Step.class);
                if(!Set.of("STARTED","SUCCEEDED","UNKNOWN").contains(step.state())
                        ||("SUCCEEDED".equals(step.state())?(step.configuredCostMicrousd()==null||step.configuredCostMicrousd()<0):
                        (step.configuredCostMicrousd()!=null||step.inputTokens()!=null||step.outputTokens()!=null)))throw unavailable();
                if(!snapshot.externalCallsPlanned()||stage==AssistantCallLedger.Stage.SEARCH&&!snapshot.execution().searchRequired())throw unavailable();
                if("SUCCEEDED".equals(step.state())) {
                    if(stage==AssistantCallLedger.Stage.SEARCH) {
                        if(step.inputTokens()!=null||step.outputTokens()!=null||step.configuredCostMicrousd()!=snapshot.execution().searchMicrousd())throw unavailable();
                    } else if(step.inputTokens()==null||step.outputTokens()==null||step.configuredCostMicrousd()!=modelCost(snapshot.execution(),step.inputTokens(),step.outputTokens())
                            ||step.configuredCostMicrousd()>metadata.modelReservedMicrousd())throw unavailable();
                }
                out.put(stage,step);
            }
            if(out.containsKey(AssistantCallLedger.Stage.MODEL)&&snapshot.execution().searchRequired()
                    &&(!out.containsKey(AssistantCallLedger.Stage.SEARCH)||!"SUCCEEDED".equals(out.get(AssistantCallLedger.Stage.SEARCH).state())))throw unavailable();
            if(!encode(out).equals(value))throw unavailable();return out;
        } catch(Exception invalid) { throw unavailable(); }
    }
    private Original original(Long customer,Long caseId,String key,Command command) {
        var entry=cases.findRequest("CUSTOMER",customer,key).orElseThrow(AgronomyAssistantCoordinator::unavailable);
        if(!entry.caseId().equals(caseId)||!entry.type().equals("ASSISTANT_REQUEST"))throw invalid("原请求编号不能用于其他问题或动作");
        var m=requests.find(entry.id()).orElseThrow(AgronomyAssistantCoordinator::unavailable);
        AgronomyAssistantWorkflow.Snapshot s;
        try {
            if(m.snapshot()==null||m.snapshot().getBytes(StandardCharsets.UTF_8).length>49152)throw unavailable();
            s=json.readValue(m.snapshot(),AgronomyAssistantWorkflow.Snapshot.class);
            var canonical=json.valueToTree(s);
            if(s.processingPlan()==null)((com.fasterxml.jackson.databind.node.ObjectNode)canonical).remove("processingPlan");
            if(s.authorization()==null)((com.fasterxml.jackson.databind.node.ObjectNode)canonical).remove("authorization");
            if(s.authorization()!=null&&(s.authorization().customerId()!=customer||s.authorization().customerAuthVersion()!=s.customerAuthVersion()))throw unavailable();
            if(!encode(canonical).equals(m.snapshot())||s.processingPlan()!=null&&!Set.of("LOCAL","EXTERNAL").contains(s.processingPlan())
                    ||s.queuedCaseVersion()!=entry.resultVersion()||s.originalCaseVersion()!=entry.expectedVersion()
                    ||s.factsVersion()!=entry.resultFactsVersion()||!s.facts().equals(entry.facts())||s.customerAuthVersion()<1
                    ||s.originalCaseVersion()<1||s.originalCaseVersion()==Long.MAX_VALUE||s.queuedCaseVersion()!=s.originalCaseVersion()+1)throw unavailable();
            var p=from(s.execution());p.requireEnabled();long model=s.externalCallsPlanned()?p.modelReservation():0,search=s.externalCallsPlanned()&&p.searchRequired()?p.searchMicrousd():0;
            boolean notDispatched="FAILED".equals(m.state())&&m.claimKey()==null;
            if(m.modelReservedMicrousd()!=(notDispatched?0:model)||m.searchReservedMicrousd()!=(notDispatched?0:search)
                    ||!Set.of("QUEUED","RUNNING","UNKNOWN","COMPLETED","FAILED").contains(m.state())||m.budgetDay()==null||m.updatedAt()==null
                    ||("QUEUED".equals(m.state())||notDispatched)!=(m.claimKey()==null))throw unavailable();
            if(m.claimKey()!=null)uuid(m.claimKey());
            decodeSteps(requests.steps(entry.id()).orElse(null),s,m);
        } catch(RuntimeException own){throw unavailable();}catch(Exception bad){throw unavailable();}
        Command original=new Command(key,Long.toString(s.originalCaseVersion()),Long.toString(s.factsVersion()),true);
        if(!hash(caseId,original,m.snapshot()).equals(entry.requestHash())||command!=null&&!hash(caseId,command,m.snapshot()).equals(entry.requestHash()))throw invalid("原AI请求内容不能修改");
        return new Original(entry,m,s);
    }
    private static AssistantBudgetPolicy from(AssistantBudgetPolicy.Execution e) {return new AssistantBudgetPolicy(true,1,1,Long.MAX_VALUE,e.model(),e.searchRequired(),e.maxOutputTokens(),e.inputMicrousdPerMillion(),e.outputMicrousdPerMillion(),e.searchMicrousd(),e.priceVersion());}
    private View view(Original o) {
        String kind="",handoff="";
        if(o.metadata().outcome()!=null){var outcome=parseTree(o.metadata().outcome());kind=outcome.path("kind").asText();handoff=outcome.path("handoffStatus").asText();}
        return new View(o.entry().requestKey(),o.entry().caseId().toString(),o.metadata().state(),Long.toString(o.entry().resultVersion()),Long.toString(o.entry().resultFactsVersion()),kind,handoff);
    }
    private String hash(Long id,Command c,String snapshot) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(java.util.List.of("ASSISTANT_REQUEST",id,c.expectedVersion(),c.factsVersion(),c.shareContextConfirmed(),snapshot)).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException("无法生成AI请求摘要");}
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception bad){throw unavailable();}}
    private com.fasterxml.jackson.databind.JsonNode parseTree(String value) {try{if(value==null||value.getBytes(StandardCharsets.UTF_8).length>49152)throw unavailable();var n=json.readTree(value);if(n==null||!n.isObject())throw unavailable();return n;}catch(Exception bad){throw unavailable();}}
    private void validateStaged(String value,AgronomyAssistantWorkflow.Snapshot snapshot) {
        if(value==null||value.getBytes(StandardCharsets.UTF_8).length>24576)throw unavailable();var root=parseTree(value);
        if("REFERENCE".equals(root.path("kind").asText())&&!new AgronomyReferencePublicationPolicy(json).eligible(value,snapshot,snapshot.facts(),snapshot.factsVersion(),clock.instant()))throw unavailable();
    }
    private static boolean fresh(AgronomyCase c,AgronomyAssistantWorkflow.Snapshot s) {return c.version()==s.queuedCaseVersion()&&c.factsVersion()==s.factsVersion()&&c.facts().equals(s.facts());}
    private boolean validAuth(Long id,Long version) {return customers.findAuthStateById(id).filter(s->s.active()&&Objects.equals(s.id(),id)&&Objects.equals(s.authVersion(),version)).isPresent();}
    private boolean validAuthorization(ClientAuthorization value) {
        return value!=null&&validAuth(value.customerId(),value.customerAuthVersion())&&authService.isCurrentClientAuthorization(value);
    }
    private void auth(ClientAuthorization value) {if(!validAuthorization(value))throw invalid("客户授权已变化，请重新登录");}
    private void requireStore(Long id) {var s=stores.find(id,true).orElseThrow(AgronomyAssistantCoordinator::unavailable);if(!Integer.valueOf(1).equals(s.status())||!Integer.valueOf(0).equals(s.deleted()))throw invalid("门店暂不能处理AI请求");}
    private boolean activeStore(Long id) {return stores.find(id,false).filter(s->Integer.valueOf(1).equals(s.status())&&Integer.valueOf(0).equals(s.deleted())).isPresent();}
    private static void validate(Command c) {if(c==null||!c.shareContextConfirmed())throw invalid("请先确认最少上下文发送");uuid(c.requestKey());number(c.expectedVersion());number(c.factsVersion());}
    private static long number(String v) {try{if(v==null||!v.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();return Long.parseLong(v);}catch(RuntimeException bad){throw invalid("AI原版本无效");}}
    private static void uuid(String v) {try{var u=UUID.fromString(v);if(!u.toString().equals(v)||u.version()!=4||u.variant()!=2)throw new IllegalArgumentException();}catch(RuntimeException bad){throw invalid("请保留原AI请求安全编号");}}
    private static void positive(Long v) {if(v==null||v<1)throw invalid("AI请求归属无效");}
    private LocalDate day() {return clock.instant().atZone(ZONE).toLocalDate();}
    private LocalDateTime now() {return clock.instant().atZone(ZONE).toLocalDateTime();}
    private static void outside() {if(TransactionSynchronizationManager.isActualTransactionActive()||com.mybatisflex.core.transaction.TransactionContext.getXID()!=null)throw new IllegalStateException("AI协调入口必须在事务外调用");}
    private <T>T inTx(Supplier<T> action) {return tx.execute(status->action.get());}
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException(message);}
    private static IllegalStateException unavailable() {return new IllegalStateException("AI原请求资料待核对");}
}
