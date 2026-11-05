package dev.vivek.orchestrator.domain;

public final class ExponentialBackoff {
    private ExponentialBackoff() { }

    public static int delaySeconds(int attemptNumber, int baseSeconds, int maxSeconds) {
        if (attemptNumber < 1 || baseSeconds < 1 || maxSeconds < 1) {
            throw new IllegalArgumentException("Attempt, base delay, and maximum delay must be positive");
        }
        long delay = baseSeconds;
        for (int i = 1; i < attemptNumber && delay < maxSeconds; i++) {
            delay = Math.min((long) maxSeconds, delay * 2L);
        }
        return (int) Math.min(delay, maxSeconds);
    }
}
