package dev.vivek.orchestrator.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "orchestrator")
@Validated
public class OrchestratorProperties {
    @Min(1) @Max(32)
    private int workerThreads = 4;

    @Min(100)
    private long dispatchIntervalMs = 500;

    @Min(15) @Max(3600)
    private int leaseDurationSeconds = 60;

    @Min(1000)
    private long heartbeatIntervalMs = 10000;

    @Min(1000)
    private long recoveryIntervalMs = 5000;

    @Min(1)
    private int baseRetryDelaySeconds = 2;

    @Min(1)
    private int maxRetryDelaySeconds = 60;

    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int workerThreads) { this.workerThreads = workerThreads; }
    public long getDispatchIntervalMs() { return dispatchIntervalMs; }
    public void setDispatchIntervalMs(long dispatchIntervalMs) { this.dispatchIntervalMs = dispatchIntervalMs; }
    public int getLeaseDurationSeconds() { return leaseDurationSeconds; }
    public void setLeaseDurationSeconds(int leaseDurationSeconds) { this.leaseDurationSeconds = leaseDurationSeconds; }
    public long getHeartbeatIntervalMs() { return heartbeatIntervalMs; }
    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) { this.heartbeatIntervalMs = heartbeatIntervalMs; }
    public long getRecoveryIntervalMs() { return recoveryIntervalMs; }
    public void setRecoveryIntervalMs(long recoveryIntervalMs) { this.recoveryIntervalMs = recoveryIntervalMs; }
    public int getBaseRetryDelaySeconds() { return baseRetryDelaySeconds; }
    public void setBaseRetryDelaySeconds(int baseRetryDelaySeconds) { this.baseRetryDelaySeconds = baseRetryDelaySeconds; }
    public int getMaxRetryDelaySeconds() { return maxRetryDelaySeconds; }
    public void setMaxRetryDelaySeconds(int maxRetryDelaySeconds) { this.maxRetryDelaySeconds = maxRetryDelaySeconds; }
}
