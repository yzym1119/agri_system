package com.agri_system.infrastructure.agronomy.assistant;

import java.time.Duration;

/** 尚未注册Spring Bean；后续收费协调器接入时才能由服务器配置构造。 */
public record DeepSeekAssistantSettings(boolean generationEnabled, boolean searchEnabled,
        String model, String generationKey, String searchKey, int maxOutputTokens, Duration timeout) {
    public static DeepSeekAssistantSettings disabled() {
        return new DeepSeekAssistantSettings(false, false, "deepseek-flash", "", "", 1024, Duration.ofSeconds(15));
    }
    @Override public String toString() { return "DeepSeekAssistantSettings[enabled=" + generationEnabled + ",search=" + searchEnabled + ",凭据隐藏]"; }
}
