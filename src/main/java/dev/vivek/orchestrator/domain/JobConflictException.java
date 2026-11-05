package dev.vivek.orchestrator.domain;

public class JobConflictException extends RuntimeException {
    public JobConflictException(String message) { super(message); }
}
