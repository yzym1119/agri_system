package com.agri_system.domain.agronomy.assistant;

import com.agri_system.domain.agronomy.model.AgronomyCase.Facts;

/** 只产生未发布结果；后续实现可信检索/专业规则前不注册运行Bean。 */
public interface AgronomyAssistantWorkflow {
    record Snapshot(long customerAuthVersion, long originalCaseVersion, long queuedCaseVersion, long factsVersion,
            String question, Facts facts, String latestSupplement, AssistantBudgetPolicy.Execution execution,String processingPlan) {
        public Snapshot(long customerAuthVersion,long originalCaseVersion,long queuedCaseVersion,long factsVersion,
                String question,Facts facts,String latestSupplement,AssistantBudgetPolicy.Execution execution) {
            this(customerAuthVersion,originalCaseVersion,queuedCaseVersion,factsVersion,question,facts,latestSupplement,execution,"EXTERNAL");
        }
        /** 旧快照缺此字段保守维持原最大预占，不重算成免费请求。 */
        public boolean externalCallsPlanned() {return processingPlan==null||"EXTERNAL".equals(processingPlan);}
        @Override public String toString() { return "AssistantSnapshot[问题与农情隐藏]"; }
    }
    String stage(Snapshot snapshot,AssistantCallLedger calls);
}
