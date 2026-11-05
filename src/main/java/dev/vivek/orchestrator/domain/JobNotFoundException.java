package dev.vivek.orchestrator.domain;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String message) { super(message); }
}
