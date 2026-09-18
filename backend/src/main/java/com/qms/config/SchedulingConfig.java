package com.qms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.ErrorHandler;

/**
 * 定时任务调度配置。
 * Spring 默认只有 1 个调度线程（scheduling-1）：任何一个定时任务抛异常或卡死，
 * 后续所有定时任务（含整点 SLA 扫描、每日预警）都会被饿死。
 * 这里改为 2 个独立线程 + 统一异常兜底，保证单次扫描失败不影响下一轮。
 */
@Slf4j
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    /** 每小时 SLA + 每日批次/留样/证照各一，2 线程足够并彼此隔离 */
    public static final int SCHED_POOL_SIZE = 2;

    @Bean(name = "qmsTaskScheduler", destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler qmsTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(SCHED_POOL_SIZE);
        scheduler.setThreadNamePrefix("qms-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setRemoveOnCancelPolicy(true);
        // 兜底：即便任务内部漏了 try/catch，也只记录本次失败，绝不中断后续调度
        scheduler.setErrorHandler(new ErrorHandler() {
            @Override
            public void handleError(Throwable t) {
                log.error("定时任务执行失败（已被调度器兜底捕获，不影响后续任务）: {}", t.getMessage(), t);
            }
        });
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(qmsTaskScheduler());
    }
}
