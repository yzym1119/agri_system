package com.agri_system.domain.agronomy.assistant;

/** 每阶段先取得持久许可再出网；不是供应商账单，不允许重发已派出的阶段。 */
public interface AssistantCallLedger {
    enum Stage { SEARCH, MODEL }
    void begin(Stage stage);
    void succeeded(Stage stage,long inputTokens,long outputTokens);
    void unknown(Stage stage);
    final class Stopped extends IllegalStateException {
        public Stopped() { super("原问题或调用许可已变化，停止后续外发"); }
    }
}
