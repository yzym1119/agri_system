package com.agri_system.application.agronomy.service;

import com.agri_system.application.staff.service.StaffAuthorityService;
import com.agri_system.application.agronomy.dto.CaseObjectIdDeserializer;
import com.agri_system.application.agronomy.dto.CaseHumanRequestDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.agri_system.domain.agronomy.model.AgronomyCase;
import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;
import com.agri_system.domain.agronomy.model.AgronomyCaseEntry;
import com.agri_system.domain.agronomy.repository.AgronomyCaseRepository;
import com.agri_system.domain.store.model.Staff;
import com.agri_system.domain.store.repository.StaffRepository;
import com.agri_system.domain.store.repository.StorePublicationRepository;
import com.agri_system.domain.system.auth.repository.SysRoleRepository;
import com.agri_system.domain.system.log.model.SysOperationLog;
import com.agri_system.domain.system.log.repository.SysOperationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgronomyCaseService {
    private final AgronomyCaseRepository rows;
    private final StaffRepository staff;
    private final SysRoleRepository roles;
    private final StaffAuthorityService authority;
    private final StorePublicationRepository stores;
    private final SysOperationLogRepository logs;
    private static final Set<String> STATES = Set.of("COLLECTING", "WAITING_REVIEW", "ACTIVE");
    public record Create(String requestKey, @JsonDeserialize(using=CaseObjectIdDeserializer.class) Long storeId, String question, Facts facts,
                         @JsonDeserialize(using=CaseHumanRequestDeserializer.class) Boolean requestHuman) {
        public Create(String requestKey, Long storeId, String question, Facts facts) { this(requestKey, storeId, question, facts, false); }
        @Override public String toString() { return "CaseCreate[问题原文隐藏]"; }
    }
    public record Change(String requestKey, String expectedVersion, String body, String sourceText,
                         Facts facts, @JsonDeserialize(using=CaseObjectIdDeserializer.class) Long assignedStaffId) {
        @Override public String toString() { return "CaseChange[正文、来源与农情隐藏]"; }
    }
    public record Receipt(String requestKey, String caseId, String entryId, String version,
                          String factsVersion, String status) {}
    public record Submission(String requestKey, String status, Receipt receipt) {}
    public record Summary(String id, String storeId, String question, Facts facts, String status,
                          String version, String factsVersion, String createdAt, String updatedAt) {}
    public record Entry(String id, String type, String actorType, String body, String sourceText,
                        Facts facts, String version, String factsVersion, String createdAt) {}
    public record Page(List<Summary> rows, String nextAfter) {}
    public record Detail(Summary problem, List<Entry> entries, String nextAfter, Entry latestReply) {}
    public record WorkSummary(Summary problem, String customerId, String assignedStaffId) {}
    public record WorkPage(List<WorkSummary> rows, String nextAfter) {}
    public record WorkDetail(WorkSummary work, List<Entry> entries, String nextAfter, Entry latestReply) {}
    private record Actor(Staff staff, boolean admin, Set<String> permissions) {
        boolean manages() { return permissions.contains("agronomy:case:manage"); }
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Receipt create(Long customerId, Create command) {
        positive(customerId);
        if (command == null) throw invalid("请填写问题");
        String key = uuid(command.requestKey()); positive(command.storeId());
        String question = text(command.question(), 2000, true, true);
        Facts facts = facts(command.facts());
        boolean requestHuman = Boolean.TRUE.equals(command.requestHuman());
        // 不改变旧未指定/false请求的摘要；明确求人工与原采集请求不能互相重放。
        String hash = digest(requestHuman ? "CREATE_WITH_HUMAN_REQUEST" : "CREATE", command.storeId(), question, facts.region(), facts.crop(), facts.growthStage());
        String initialState = requestHuman ? "WAITING_REVIEW" : "COLLECTING";
        var existing = rows.findCreation(customerId, key);
        if (existing.isPresent()) return creationReceipt(existing.get(), hash);
        var now = now();
        boolean inserted = rows.insertCase(new AgronomyCase(null, customerId, command.storeId(), key, hash,
                question, facts, initialState, 1, 1, null, now, now));
        var saved = rows.findCreation(customerId, key).orElseThrow(AgronomyCaseService::unavailable);
        if (!inserted) return creationReceipt(saved, hash);
        requireStore(saved.storeId(), true);
        var first = new AgronomyCaseEntry(null, saved.id(), "CUSTOMER", customerId, key, hash,
                "QUESTION", question, "", facts, null, 0, 1, 1, initialState, now);
        if (!rows.insertEntry(first)) throw reused();
        return receipt(rows.findRequest("CUSTOMER", customerId, key).orElseThrow(AgronomyCaseService::unavailable));
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Receipt changeCustomer(Long customerId, Long id, String action, Change command) {
        positive(customerId); positive(id);
        if (!Set.of("SUPPLEMENT", "HUMAN_REQUEST").contains(action == null ? "" : action)) throw invalid("客户操作无效");
        var row = rows.findOwned(id, customerId, true).orElseThrow(AgronomyCaseService::unavailable);
        return change(row, "CUSTOMER", customerId, action, command);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public Receipt changeStaff(Long actorId, Long id, String action, Change command) {
        var actor = actor(actorId); positive(id);
        if (!Set.of("ASSIGN", "HUMAN_REPLY").contains(action == null ? "" : action)) throw invalid("员工操作无效");
        requirePermission(actor, action.equals("ASSIGN") ? "agronomy:case:manage" : "agronomy:case:review");
        var row = rows.find(id, true).orElseThrow(AgronomyCaseService::unavailable);
        // 重新分配不抹除本员工的原提交事实。只准核原号、原问题、原动作；
        // change仍逐项核摘要，原收据分支不会读取后续正文或新增追加。
        boolean ownsOriginal = command != null && command.requestKey() != null
                && rows.findRequest("STAFF", actorId, command.requestKey())
                .filter(e -> e.caseId().equals(id) && e.type().equals(action)).isPresent();
        if (ownsOriginal) storeScope(actor, row.storeId());
        else scope(actor, row);
        return change(row, "STAFF", actorId, action, command);
    }

    private Receipt change(AgronomyCase row, String actorType, Long actorId, String action, Change command) {
        integrity(row);
        if (command == null) throw invalid("请确认原问题与操作内容");
        String key = uuid(command.requestKey()); long expected = version(command.expectedVersion());
        String body = text(command.body(), 4000, true, !action.equals("ASSIGN"));
        String source = text(command.sourceText(), 2000, true, action.equals("HUMAN_REPLY"));
        Facts submitted = command.facts() == null ? null : facts(command.facts());
        if (!action.equals("SUPPLEMENT") && submitted != null
                || !action.equals("ASSIGN") && command.assignedStaffId() != null
                || !action.equals("HUMAN_REPLY") && !source.isEmpty()) throw invalid("操作字段与当前动作不一致");
        if (action.equals("SUPPLEMENT") && submitted == null) throw invalid("请一并确认当前地区、作物与阶段，不清楚可留空");
        if (action.equals("ASSIGN")) positive(command.assignedStaffId());
        String hash = digest(action, row.id(), expected, body, source,
                submitted == null ? null : submitted.region(), submitted == null ? null : submitted.crop(),
                submitted == null ? null : submitted.growthStage(), command.assignedStaffId());
        var old = rows.findRequest(actorType, actorId, key);
        if (old.isPresent()) return replay(old.get(), row.id(), hash);
        if (expected != row.version() || expected == Long.MAX_VALUE) throw stale();
        requireStore(row.storeId(), false);
        Facts current = submitted == null ? row.facts() : submitted;
        long factsVersion = row.factsVersion();
        if (action.equals("SUPPLEMENT")) {
            if (factsVersion == Long.MAX_VALUE) throw stale();
            factsVersion++;
        }
        Long assigned = row.assignedStaffId();
        if (action.equals("ASSIGN")) {
            var target = staff.findIdentityById(command.assignedStaffId()).orElseThrow(() -> invalid("承办员工不存在或不在职"));
            if (!command.assignedStaffId().equals(target.getId()) || !target.isActive()
                    || !Integer.valueOf(0).equals(target.getDeleted()) || !row.storeId().equals(target.getStoreId()))
                throw invalid("只能分配给本店在职员工");
            if (!authority.loadPermissions(target.getId()).contains("agronomy:case:query"))
                throw invalid("承办员工尚无农技问题查询权限");
            assigned = target.getId();
        }
        String state = switch (action) {
            case "HUMAN_REQUEST" -> "WAITING_REVIEW";
            case "HUMAN_REPLY" -> "ACTIVE";
            case "SUPPLEMENT" -> row.status().equals("WAITING_REVIEW") ? "WAITING_REVIEW" : "COLLECTING";
            default -> row.status();
        };
        var now = now();
        if (!rows.update(row.id(), expected, current, state, factsVersion, assigned, now)) throw stale();
        var event = new AgronomyCaseEntry(null, row.id(), actorType, actorId, key, hash, action, body,
                source, current, action.equals("ASSIGN") ? assigned : null, expected, expected + 1, factsVersion, state, now);
        if (!rows.insertEntry(event)) throw reused();
        if (actorType.equals("STAFF")) audit(actorId, row.id(), action, expected, hash);
        return receipt(rows.findRequest(actorType, actorId, key).orElseThrow(AgronomyCaseService::unavailable));
    }

    public Submission submissionCustomer(Long customerId, String key) {
        positive(customerId); uuid(key);
        var entry = rows.findRequest("CUSTOMER", customerId, key);
        if (entry.isEmpty()) return new Submission(key, "UNKNOWN", null);
        var owner = rows.findOwned(entry.get().caseId(), customerId, false).orElseThrow(AgronomyCaseService::unavailable);
        integrity(owner);
        return new Submission(key, "COMPLETED", receipt(entry.get()));
    }
    public Submission submissionStaff(Long actorId, String key) {
        var actor = actor(actorId); uuid(key);
        var entry = rows.findRequest("STAFF", actorId, key);
        if (entry.isEmpty()) return new Submission(key, "UNKNOWN", null);
        var row = rows.find(entry.get().caseId(), false).orElseThrow(AgronomyCaseService::unavailable);
        // 只返回当前员工自己的不可变收据，不开放重新分配后的问题详情。
        storeScope(actor, row.storeId());
        integrity(row);
        requirePermission(actor, entry.get().type().equals("ASSIGN") ? "agronomy:case:manage" : "agronomy:case:review");
        return new Submission(key, "COMPLETED", receipt(entry.get()));
    }
    public Page pageCustomer(Long customerId, Long before, String status) {
        positive(customerId); cursor(before); status(status);
        var raw = rows.page(customerId, null, null, status, before);
        var page = limited(raw);
        if (page.stream().anyMatch(c -> !customerId.equals(c.customerId()))) throw unavailable();
        return new Page(page.stream().map(AgronomyCaseService::summary).toList(), next(raw));
    }
    public WorkPage pageStaff(Long actorId, Long storeId, Long before, String status) {
        var actor = actor(actorId); cursor(before); status(status);
        if (storeId != null) { positive(storeId); storeScope(actor, storeId); }
        Long actualStore = actor.admin() && actor.manages() ? storeId : actor.staff().getStoreId();
        var raw = rows.page(null, actualStore, actor.manages() ? null : actorId, status, before);
        var page = limited(raw); page.forEach(c -> scope(actor, c));
        return new WorkPage(page.stream().map(AgronomyCaseService::work).toList(), next(raw));
    }
    public Detail detailCustomer(Long customerId, Long id, Long before) {
        positive(customerId); positive(id); cursor(before);
        var row = rows.findOwned(id, customerId, false).orElseThrow(AgronomyCaseService::unavailable);
        var entries = entries(row, true, before);
        return new Detail(summary(row), entries.values(), entries.next(), latestReply(row));
    }
    public WorkDetail detailStaff(Long actorId, Long id, Long before) {
        var actor = actor(actorId); positive(id); cursor(before);
        var row = rows.find(id, false).orElseThrow(AgronomyCaseService::unavailable); scope(actor, row);
        var entries = entries(row, false, before);
        return new WorkDetail(work(row), entries.values(), entries.next(), latestReply(row));
    }
    private record Entries(List<Entry> values, String next) {}
    private Entries entries(AgronomyCase row, boolean customer, Long before) {
        integrity(row);
        var raw = rows.entries(row.id(), customer, row.version(), before);
        var page = raw.subList(0, Math.min(20, raw.size()));
        for (var e : page) if (!e.caseId().equals(row.id()) || e.resultVersion() > row.version()
                || customer && !customerVisible(e)) throw unavailable();
        return new Entries(page.stream().map(AgronomyCaseService::entry).toList(), raw.size() > 20 ? page.get(19).id().toString() : null);
    }
    private Entry latestReply(AgronomyCase row) {
        return rows.latestHumanReply(row.id(), row.version()).map(e -> {
            if (!row.id().equals(e.caseId()) || !e.type().equals("HUMAN_REPLY") || !e.actorType().equals("STAFF")
                    || e.resultVersion() > row.version() || e.resultFactsVersion() > row.factsVersion()) throw unavailable();
            return entry(e);
        }).orElse(null);
    }
    private static boolean customerVisible(AgronomyCaseEntry e) {
        return e.actorType().equals("CUSTOMER") && Set.of("QUESTION", "SUPPLEMENT", "HUMAN_REQUEST").contains(e.type())
                || e.actorType().equals("STAFF") && e.type().equals("HUMAN_REPLY");
    }
    private static Entry entry(AgronomyCaseEntry e) {
        positive(e.id());
        if (e.resultVersion() <= 0 || e.resultFactsVersion() <= 0 || e.resultFactsVersion() > e.resultVersion() || e.createdAt() == null) throw unavailable();
        return new Entry(e.id().toString(), e.type(), e.actorType(), e.body(), e.sourceText(), e.facts(),
                Long.toString(e.resultVersion()), Long.toString(e.resultFactsVersion()), e.createdAt().toString());
    }
    private Receipt creationReceipt(AgronomyCase row, String hash) {
        integrity(row);
        if (!hash.equals(row.requestHash())) throw reused();
        var first = rows.findRequest("CUSTOMER", row.customerId(), row.requestKey()).orElseThrow(AgronomyCaseService::unavailable);
        if (!first.type().equals("QUESTION") || first.expectedVersion() != 0 || first.resultVersion() != 1) throw unavailable();
        return replay(first, row.id(), hash);
    }
    private static Receipt replay(AgronomyCaseEntry e, Long id, String hash) {
        if (!id.equals(e.caseId()) || !hash.equals(e.requestHash())) throw reused();
        return receipt(e);
    }
    private static Receipt receipt(AgronomyCaseEntry e) {
        positive(e.id()); positive(e.caseId());
        if (e.resultVersion() <= 0 || e.resultFactsVersion() <= 0 || !STATES.contains(e.resultStatus())) throw unavailable();
        return new Receipt(e.requestKey(), e.caseId().toString(), e.id().toString(),
                Long.toString(e.resultVersion()), Long.toString(e.resultFactsVersion()), e.resultStatus());
    }
    private Actor actor(Long id) {
        positive(id);
        var s = staff.findIdentityById(id).orElseThrow(AgronomyCaseService::unavailable);
        if (!id.equals(s.getId()) || !s.isActive() || !Integer.valueOf(0).equals(s.getDeleted())) throw unavailable();
        var codes = roles.findRoleCodesByStaffId(id);
        var actor = new Actor(s, codes.contains("ADMIN") || codes.contains("SUPER_ADMIN"), Set.copyOf(authority.loadPermissions(id)));
        requirePermission(actor, "agronomy:case:query");
        if (!actor.admin() || !actor.manages()) positive(s.getStoreId());
        return actor;
    }
    private static void requirePermission(Actor a, String p) {
        if (!a.permissions().contains(p)) throw invalid("没有处理该农技事项的权限");
    }
    private static void storeScope(Actor a, Long store) {
        if (!(a.admin() && a.manages()) && !Objects.equals(store, a.staff().getStoreId())) throw unavailable();
    }
    private static void scope(Actor a, AgronomyCase c) {
        integrity(c); storeScope(a, c.storeId());
        if (!a.manages() && !Objects.equals(a.staff().getId(), c.assignedStaffId())) throw unavailable();
    }
    private void requireStore(Long id, boolean newlyCreated) {
        var store = stores.find(id, true).orElseThrow(() -> invalid("门店暂时不能接收问题"));
        if (!id.equals(store.id()) || !Integer.valueOf(0).equals(store.deleted()) || !Integer.valueOf(1).equals(store.status())
                || newlyCreated && !Integer.valueOf(1).equals(store.visible())) throw invalid("门店暂时不能接收问题");
    }
    private void audit(Long actor, Long id, String action, long version, String hash) {
        var log = new SysOperationLog(); log.setStaffId(actor); log.setModule("农技问题");
        log.setAction(action.equals("ASSIGN") ? "分配农技问题" : "人工回复农技问题");
        log.setMethod("POST"); log.setRequestUri("/api/admin/agronomy/cases/" + id);
        log.setRequestParams("问题=" + id + ";原版本=" + version + ";请求摘要=" + hash);
        log.setCreatedAt(now()); log.setStatus(1); logs.save(log);
    }
    private static Summary summary(AgronomyCase c) {
        integrity(c);
        return new Summary(c.id().toString(), c.storeId().toString(), c.question(), c.facts(), c.status(),
                Long.toString(c.version()), Long.toString(c.factsVersion()), c.createdAt().toString(), c.updatedAt().toString());
    }
    private static WorkSummary work(AgronomyCase c) {
        return new WorkSummary(summary(c), c.customerId().toString(), c.assignedStaffId() == null ? null : c.assignedStaffId().toString());
    }
    private static List<AgronomyCase> limited(List<AgronomyCase> list) { return list.subList(0, Math.min(20, list.size())); }
    private static String next(List<AgronomyCase> list) { return list.size() > 20 ? list.get(19).id().toString() : null; }
    private static Facts facts(Facts f) {
        return f == null ? new Facts("", "", "") : new Facts(text(f.region(), 100, false, false).strip(),
                text(f.crop(), 50, false, false).strip(), text(f.growthStage(), 100, false, false).strip());
    }
    private static void integrity(AgronomyCase c) {
        positive(c.id()); positive(c.customerId()); positive(c.storeId());
        if (!STATES.contains(c.status()) || c.version() <= 0 || c.factsVersion() <= 0 || c.factsVersion() > c.version()
                || c.facts() == null || c.createdAt() == null || c.updatedAt() == null) throw unavailable();
    }
    private static void status(String s) { if (s != null && !STATES.contains(s)) throw invalid("问题状态无效"); }
    private static void cursor(Long id) { if (id != null) positive(id); }
    private static void positive(Long id) { if (id == null || id <= 0) throw invalid("身份或问题编号无效"); }
    private static long version(String value) {
        try { if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw stale(); return Long.parseLong(value); }
        catch (NumberFormatException bad) { throw stale(); }
    }
    private static String uuid(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value) || value.charAt(14) != '4'
                    || "89ab".indexOf(value.charAt(19)) < 0) throw reused();
            return value;
        } catch (RuntimeException bad) { throw invalid("请保留原操作的安全请求编号"); }
    }
    private static String text(String value, int max, boolean multi, boolean required) {
        String s = value == null ? "" : value;
        if (s.length() > max || required && s.isBlank() || (multi ? s.replace("\n", "").replace("\r", "") : s)
                .chars().anyMatch(c -> c < 32 || c == 127)) throw invalid("文字为空、过长或包含无效字符");
        return s;
    }
    private static String digest(Object... values) {
        var input = new StringBuilder();
        for (Object v : values) { if (v == null) input.append('N'); else { String s = v.toString(); input.append('S').append(s.length()).append(':').append(s); } }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    private static IllegalArgumentException unavailable() { return invalid("问题不存在、不可查看或资料待核对"); }
    private static IllegalArgumentException stale() { return invalid("问题情况已变化，请重新读取并确认后再操作"); }
    private static IllegalArgumentException reused() { return invalid("原请求已用于其他内容或操作，请核对原提交"); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
