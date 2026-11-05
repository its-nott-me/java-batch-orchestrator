package dev.vivek.orchestrator.handler;

import dev.vivek.orchestrator.domain.PermanentJobException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobHandlerRegistryTest {
    @Test
    void registersAndSortsSupportedTypes() {
        JobHandler echo = handler("ECHO");
        JobHandler wait = handler("WAIT");
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(wait, echo));
        assertEquals(List.of("ECHO", "WAIT"), registry.supportedTypes());
    }

    @Test
    void rejectsDuplicateAndUnknownHandlers() {
        assertThrows(IllegalStateException.class,
                () -> new JobHandlerRegistry(List.of(handler("ECHO"), handler("ECHO"))));
        assertThrows(PermanentJobException.class,
                () -> new JobHandlerRegistry(List.of()).require("MISSING"));
    }

    private JobHandler handler(String type) {
        return new JobHandler() {
            @Override public String type() { return type; }
            @Override public JsonNode execute(JsonNode payload, JobExecutionContext context) { return payload; }
        };
    }
}
