package dev.vivek.orchestrator.handler;

import dev.vivek.orchestrator.domain.PermanentJobException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JobHandlerRegistry {
    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> discoveredHandlers) {
        Map<String, JobHandler> registered = new HashMap<>();
        for (JobHandler handler : discoveredHandlers) {
            JobHandler previous = registered.putIfAbsent(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate job handler registered for type: " + handler.type());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public JobHandler require(String type) {
        JobHandler handler = handlers.get(type);
        if (handler == null) {
            throw new PermanentJobException("No handler is registered for job type: " + type);
        }
        return handler;
    }

    public List<String> supportedTypes() {
        return handlers.keySet().stream().sorted().toList();
    }
}
