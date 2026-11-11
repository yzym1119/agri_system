package com.agri_system.domain.agronomy.assistant;

import java.math.BigInteger;

/** 服务器配置的应用预占；微美元，非供应商账单或用户付费授权。 */
public record AssistantBudgetPolicy(boolean enabled, long dailyModelCalls, long dailySearchCalls,
        long dailyMicrousd, String model, boolean searchRequired, int maxOutputTokens,
        long inputMicrousdPerMillion, long outputMicrousdPerMillion, long searchMicrousd, String priceVersion) {
    public static AssistantBudgetPolicy disabled() {
        return new AssistantBudgetPolicy(false,0,0,0,"deepseek-flash",false,1024,0,0,0,"");
    }
    public record Execution(String model, boolean searchRequired, int maxOutputTokens,
            long inputMicrousdPerMillion, long outputMicrousdPerMillion, long searchMicrousd, String priceVersion) {}
    public Execution execution() { return new Execution(model,searchRequired,maxOutputTokens,inputMicrousdPerMillion,outputMicrousdPerMillion,searchMicrousd,priceVersion); }
    public void requireEnabled() {
        if (!enabled || dailyModelCalls<1 || dailySearchCalls<0 || searchRequired&&dailySearchCalls<1 || dailyMicrousd<1
                || !("deepseek-flash".equals(model)||"deepseek-v4-pro".equals(model)) || maxOutputTokens<1||maxOutputTokens>2048
                || inputMicrousdPerMillion<=0||outputMicrousdPerMillion<=0||searchRequired&&searchMicrousd<=0||searchMicrousd<0
                || priceVersion==null||!priceVersion.matches("[A-Za-z0-9._-]{1,80}")) throw new IllegalArgumentException("AI尚未配置有效的调用额度与计价");
    }
    public long modelReservation() {
        requireEnabled();
        try {
            var million=BigInteger.valueOf(1000000);
            return BigInteger.valueOf(inputMicrousdPerMillion).multiply(BigInteger.valueOf(32768))
                    .add(BigInteger.valueOf(outputMicrousdPerMillion).multiply(BigInteger.valueOf(maxOutputTokens)))
                    .add(million.subtract(BigInteger.ONE)).divide(million).longValueExact();
        } catch (ArithmeticException invalid) { throw new IllegalArgumentException("AI计价超出支持范围"); }
    }
}
