package com.agri_system.domain.agronomy.assistant;

/** 只生成未发布草案。应用层须先持久预占/取得调用许可，再独立校验输出。 */
public interface AgronomyModelGateway {
    record Input(String systemPrompt, String contextJson) {
        @Override public String toString() { return "AssistantModelInput[上下文隐藏]"; }
    }
    record Output(String requestedModel, String draftJson, long inputTokens, long outputTokens) {
        @Override public String toString() { return "AssistantModelOutput[草案隐藏]"; }
    }
    Output generate(Input input);
}
