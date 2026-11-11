package com.agri_system.domain.agronomy.assistant;

/** 不保留底层异常、URL参数或网页原文。 */
public final class AgronomySourceFailure extends RuntimeException {
    public enum Reason { DISABLED, INPUT, NETWORK, DEADLINE, HTTP, CONTENT }
    private final Reason reason;
    public AgronomySourceFailure(Reason reason){super("官方资料暂不能核实："+reason.name());this.reason=reason;}
    public Reason reason(){return reason;}
}
