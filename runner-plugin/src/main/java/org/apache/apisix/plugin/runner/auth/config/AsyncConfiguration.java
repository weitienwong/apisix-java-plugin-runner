package org.apache.apisix.plugin.runner.auth.config;

import io.netty.util.concurrent.DefaultThreadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author wangweitian
 */
@Configuration
public class AsyncConfiguration {
    @Bean("task-pool")
    public Executor executor() {
        ThreadFactory factory = new DefaultThreadFactory("async-exec-%d");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cpuNum = Runtime.getRuntime().availableProcessors();

        // 线程池维护线程的最少数量
        executor.setCorePoolSize(cpuNum/4 + 1);
        // 线程池维护线程的最大数量
        executor.setMaxPoolSize(cpuNum + 1);
        // 缓存队列
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(300);
        // 线程名
        executor.setThreadFactory(factory);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 线程池初始化
        executor.initialize();

        return executor;
    }
}
