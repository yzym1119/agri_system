package com.agri_system.application.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度开关。
 *
 * <p>core-application 里的所有 {@code @Scheduled} 任务(VIP 降级/月度权益、积分年度清零、
 * 订单自动完成、提成结算/解冻等)会被每个扫描 {@code com.agri_system} 的可部署应用加载。
 * 若 api-admin 与 api-client 同时运行,同一批任务会被双跑:积分清零会重复写负数流水、
 * 月结算会并发 upsert。故调度归属由 {@code app.scheduling.enabled} 统一控制:</p>
 * <ul>
 *   <li>api-admin:{@code true}(缺省亦为 true),承担全部定时任务;</li>
 *   <li>api-client:{@code false},不注册 {@code @EnableScheduling},{@code @Scheduled} 不生效。</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}

