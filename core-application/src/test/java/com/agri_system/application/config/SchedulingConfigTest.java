package com.agri_system.application.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调度门控回归:确保 api-client(app.scheduling.enabled=false)不加载 @EnableScheduling,
 * 从而所有 @Scheduled 任务只在 api-admin 单进程执行,避免双跑。
 */
class SchedulingConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class);

    @Test
    void enabledWhenPropertyMissing() {
        // 缺省(如 api-admin 未显式配置的历史部署)仍启用,保持既有行为
        runner.run(ctx -> assertThat(ctx).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void enabledWhenPropertyTrue() {
        runner.withPropertyValues("app.scheduling.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(SchedulingConfig.class));
    }

    @Test
    void disabledWhenPropertyFalse() {
        runner.withPropertyValues("app.scheduling.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(SchedulingConfig.class));
    }
}
