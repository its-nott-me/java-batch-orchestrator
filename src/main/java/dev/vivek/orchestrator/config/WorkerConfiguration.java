package dev.vivek.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class WorkerConfiguration {
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor jobWorkerPool(OrchestratorProperties properties) {
        int threads = properties.getWorkerThreads();
        AtomicInteger sequence = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                task -> {
                    Thread thread = new Thread(task, "job-worker-" + sequence.incrementAndGet());
                    thread.setDaemon(false);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.prestartAllCoreThreads();
        return executor;
    }

    @Bean
    public Semaphore workerPermits(OrchestratorProperties properties) {
        return new Semaphore(properties.getWorkerThreads(), true);
    }
}
