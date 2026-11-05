package dev.vivek.orchestrator.handler;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class EchoJobHandler implements JobHandler {
    private final JsonMapper jsonMapper;

    public EchoJobHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String type() { return "ECHO"; }

    @Override
    public JsonNode execute(JsonNode payload, JobExecutionContext context) {
        String message = payload.path("message").asString("Hello from the batch orchestrator");
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("message", message);
        result.put("jobId", context.jobId().toString());
        result.put("attempt", context.attemptNumber());
        result.put("completedAt", Instant.now().toString());
        return result;
    }
}
