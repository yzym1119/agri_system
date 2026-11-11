package com.agri_system.infrastructure.agronomy.assistant;

import com.agri_system.domain.agronomy.assistant.AgronomyModelGateway;
import com.agri_system.domain.agronomy.assistant.AgronomySearchGateway;
import com.agri_system.domain.agronomy.assistant.AssistantGatewayFailure;
import com.agri_system.domain.agronomy.assistant.AssistantGatewayFailure.Reason;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 固定供应商边界。不注册Bean；只有后续持久收费许可协调器可以接入客户端流程。 */
public final class DeepSeekAssistantGateway implements AgronomyModelGateway, AgronomySearchGateway {
    private static final URI MODEL = URI.create("https://api.deepseek.com/chat/completions");
    private static final URI SEARCH = URI.create("https://api.deepseek.com/v1/tools/search");
    private static final int MAX_REQUEST_BYTES = 24576;
    private static final int MAX_RESPONSE_BYTES = 262144;
    private final ObjectMapper json;
    private final DeepSeekAssistantSettings settings;
    private final HttpClient http;

    public DeepSeekAssistantGateway(ObjectMapper json, DeepSeekAssistantSettings settings) {
        this(json, settings, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build());
    }
    /** 传输替身只供同包测试；生产端点不能配置为任意URL。 */
    DeepSeekAssistantGateway(ObjectMapper json, DeepSeekAssistantSettings settings, HttpClient http) {
        if (json == null || settings == null || http == null) throw failure(Reason.CONFIGURATION);
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.json.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(32).maxStringLength(MAX_RESPONSE_BYTES).maxNumberLength(32).build());
        this.settings = settings;
        this.http = http;
    }

    @Override public Output generate(Input input) {
        if (!settings.generationEnabled()) throw failure(Reason.DISABLED);
        configuration(settings.generationKey());
        if (!("deepseek-flash".equals(settings.model()) || "deepseek-v4-pro".equals(settings.model()))
                || settings.maxOutputTokens() < 1 || settings.maxOutputTokens() > 2048) throw failure(Reason.CONFIGURATION);
        if (input == null) throw failure(Reason.INPUT);
        String system = text(input.systemPrompt(), MAX_REQUEST_BYTES, true, true, Reason.INPUT);
        String context = text(input.contextJson(), MAX_REQUEST_BYTES, true, true, Reason.INPUT);
        object(context, Reason.INPUT);
        byte[] request = encode(Map.of("model", settings.model(), "messages", List.of(
                Map.of("role", "system", "content", system), Map.of("role", "user", "content", context)),
                "thinking", Map.of("type", "disabled"), "response_format", Map.of("type", "json_object"),
                "max_tokens", settings.maxOutputTokens(), "stream", false));
        JsonNode data = send(MODEL, settings.generationKey(), request);
        var choices = data.path("choices");
        if (!choices.isArray() || choices.size() != 1) throw failure(Reason.RESPONSE);
        var choice = choices.get(0); var message = choice.path("message");
        if (!"stop".equals(choice.path("finish_reason").asText()) || !"assistant".equals(message.path("role").asText())
                || message.hasNonNull("tool_calls") && (!message.path("tool_calls").isArray() || !message.path("tool_calls").isEmpty())
                || message.hasNonNull("refusal") && (!message.path("refusal").isTextual() || !message.path("refusal").asText().isEmpty())
                || message.hasNonNull("function_call")
                || !message.path("content").isTextual()) throw failure(Reason.RESPONSE);
        var draft = object(text(message.path("content").textValue(), 32768, true, true, Reason.RESPONSE), Reason.RESPONSE);
        var usage = data.path("usage");
        long inputTokens = integer(usage.path("prompt_tokens")), outputTokens = integer(usage.path("completion_tokens"));
        long total = integer(usage.path("total_tokens"));
        if (inputTokens == 0 || outputTokens == 0 || inputTokens > 32768 || outputTokens > settings.maxOutputTokens() || total != inputTokens + outputTokens) throw failure(Reason.RESPONSE);
        // 供应商草案未经过农技发布规则；此值不得直接当最终客户答复。
        return new Output(settings.model(), draft.toString(), inputTokens, outputTokens);
    }

    @Override public List<Candidate> search(String query) {
        if (!settings.searchEnabled()) throw failure(Reason.DISABLED);
        configuration(settings.searchKey());
        text(query, 256, false, true, Reason.INPUT);
        if (query.codePointCount(0, query.length()) > 128) throw failure(Reason.INPUT);
        var data = send(SEARCH, settings.searchKey(), encode(Map.of("query", query, "count", 3, "need_content", true)));
        var results = data.path("results");
        if (!results.isArray() || results.size() > 3) throw failure(Reason.RESPONSE);
        var values = new ArrayList<Candidate>();
        for (var row : results) {
            String title = text(string(row, "title"), 500, false, true, Reason.RESPONSE);
            String url = text(string(row, "url"), 2048, false, true, Reason.RESPONSE);
            try {
                var uri = URI.create(url);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null) throw failure(Reason.RESPONSE);
            } catch (IllegalArgumentException invalid) { throw failure(Reason.RESPONSE); }
            String content = optionalText(row, "content");
            if (content.isBlank()) content = optionalText(row, "summary");
            if (content.isBlank()) content = optionalText(row, "snippet");
            OffsetDateTime date = null;
            if (row.hasNonNull("date")) {
                try { date = OffsetDateTime.parse(text(string(row, "date"), 64, false, true, Reason.RESPONSE)); }
                catch (RuntimeException invalid) { throw failure(Reason.RESPONSE); }
            }
            int end = Math.min(content.length(), 8000);
            if (end > 0 && Character.isHighSurrogate(content.charAt(end - 1))) end--;
            values.add(new Candidate(title, url, content.substring(0, end), end < content.length(), date));
        }
        return List.copyOf(values);
    }

    private void configuration(String key) {
        if (key == null || key.length() > 2048 || !key.matches("[A-Za-z0-9._~+/-]+=*")
                || settings.timeout() == null || settings.timeout().isNegative() || settings.timeout().isZero()
                || settings.timeout().compareTo(Duration.ofSeconds(20)) > 0) throw failure(Reason.CONFIGURATION);
    }
    private byte[] encode(Map<String, Object> body) {
        try {
            byte[] data = json.writeValueAsBytes(body);
            if (data.length > MAX_REQUEST_BYTES) throw failure(Reason.INPUT);
            return data;
        } catch (AssistantGatewayFailure own) { throw own; }
        catch (Exception invalid) { throw failure(Reason.INPUT); }
    }
    private JsonNode object(String raw, Reason reason) {
        try {
            var value = json.readTree(raw);
            if (value == null || !value.isObject()) throw failure(reason);
            validateTree(value, reason);
            return value;
        } catch (AssistantGatewayFailure own) { throw own; }
        catch (Exception invalid) { throw failure(reason); }
    }
    private static void validateTree(JsonNode value, Reason reason) {
        if (value.isTextual()) text(value.textValue(), MAX_RESPONSE_BYTES, true, false, reason);
        if (value.isFloatingPointNumber() && !Double.isFinite(value.doubleValue())) throw failure(reason);
        if (value.isObject()) value.fieldNames().forEachRemaining(name -> text(name, MAX_RESPONSE_BYTES, false, false, reason));
        if (value.isContainerNode()) for (var child : value) validateTree(child, reason);
    }
    private JsonNode send(URI destination, String key, byte[] body) {
        var subscriber = new LimitedBody(); CompletableFuture<HttpResponse<byte[]>> pending = null;
        long deadline = System.nanoTime() + settings.timeout().toNanos();
        try {
            var request = HttpRequest.newBuilder(destination).timeout(settings.timeout()).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            pending = http.sendAsync(request, ignored -> subscriber);
            var response = pending.get(Math.max(1L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) throw failure(Reason.HTTP_REJECTED);
            if (response.body() == null || response.body().length > MAX_RESPONSE_BYTES) throw failure(Reason.RESPONSE);
            var data = json.readTree(response.body());
            if (data == null || !data.isObject()) throw failure(Reason.RESPONSE);
            return data;
        } catch (InterruptedException interrupted) {
            abort(pending, subscriber); Thread.currentThread().interrupt(); throw failure(Reason.NETWORK);
        } catch (TimeoutException timeout) {
            abort(pending, subscriber); throw failure(Reason.DEADLINE);
        } catch (ExecutionException invalid) {
            abort(pending, subscriber);
            if (invalid.getCause() instanceof AssistantGatewayFailure own) throw own;
            throw failure(Reason.NETWORK);
        } catch (AssistantGatewayFailure own) { abort(pending, subscriber); throw own; }
        catch (Exception invalid) { abort(pending, subscriber); throw failure(Reason.RESPONSE); }
    }
    private static void abort(CompletableFuture<?> pending, LimitedBody subscriber) {
        if (pending != null) pending.cancel(true);
        subscriber.cancel();
    }
    private static long integer(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0 || value.longValue() > 65536) throw failure(Reason.RESPONSE);
        return value.longValue();
    }
    private static String string(JsonNode row, String name) {
        if (!row.path(name).isTextual()) throw failure(Reason.RESPONSE);
        return row.path(name).textValue();
    }
    private static String optionalText(JsonNode row, String field) {
        return !row.hasNonNull(field) ? "" : text(string(row, field), MAX_RESPONSE_BYTES, true, false, Reason.RESPONSE);
    }
    private static String text(String value, int max, boolean multi, boolean required, Reason reason) {
        if (value == null || value.length() > max || required && value.isBlank()) throw failure(reason);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 && !(multi && (c == '\n' || c == '\r' || c == '\t')) || c == 127) throw failure(reason);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) throw failure(reason);
            } else if (Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(value.charAt(i - 1)))) throw failure(reason);
        }
        return value;
    }
    private static AssistantGatewayFailure failure(Reason reason) { return new AssistantGatewayFailure(reason); }

    /** 按实际流字节数限制内存；外层截止时也取消可能迟到的上游订阅。 */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription incoming) {
            if (!subscription.compareAndSet(null, incoming) || cancelled.get()) { incoming.cancel(); return; }
            incoming.request(Long.MAX_VALUE);
        }
        @Override public void onNext(List<ByteBuffer> parts) {
            if (result.isDone()) return;
            for (var part : parts) {
                if (part.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    result.completeExceptionally(failure(Reason.RESPONSE)); cancel(); return;
                }
                byte[] chunk = new byte[part.remaining()]; part.get(chunk); bytes.writeBytes(chunk);
            }
        }
        @Override public void onError(Throwable ignored) { result.completeExceptionally(failure(Reason.NETWORK)); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
        void cancel() {
            cancelled.set(true); var current = subscription.get(); if (current != null) current.cancel();
            result.completeExceptionally(failure(Reason.NETWORK));
        }
    }
}
