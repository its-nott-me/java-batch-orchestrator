package dev.vivek.orchestrator.handler;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class FailOnceJobHandler implements JobHandler {
    private final JsonMapper jsonMapper;

    public FailOnceJobHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String type() { return "FAIL_ONCE"; }

    @Override
    public JsonNode execute(JsonNode payload, JobExecutionContext context) {
        if (context.attemptNumber() == 1) {
            throw new IllegalStateException("Intentional first-attempt failure: demonstrating retry and backoff");
        }
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("message", payload.path("message").asString("Recovered after retry"));
        result.put("succeededOnAttempt", context.attemptNumber());
        result.put("completedAt", Instant.now().toString());
        return result;
    }
}
