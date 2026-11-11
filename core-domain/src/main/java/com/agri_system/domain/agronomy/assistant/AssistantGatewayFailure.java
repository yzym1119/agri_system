package com.agri_system.domain.agronomy.assistant;

/** 对外只传稳定分类；不携带供应商原文、凭据URL或原异常cause。 */
public final class AssistantGatewayFailure extends RuntimeException {
    public enum Reason { DISABLED, CONFIGURATION, INPUT, DEADLINE, NETWORK, HTTP_REJECTED, RESPONSE }
    private final Reason reason;
    public AssistantGatewayFailure(Reason reason) {
        super("AI调用尚未完成：" + reason.name(), null, false, true);
        this.reason = reason;
    }
    public Reason reason() { return reason; }
}
