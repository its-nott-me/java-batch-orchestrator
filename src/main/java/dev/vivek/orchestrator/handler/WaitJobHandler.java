package dev.vivek.orchestrator.handler;

import dev.vivek.orchestrator.domain.PermanentJobException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Component
public class WaitJobHandler implements JobHandler {
    private static final long MAX_WAIT_MS = 30_000;
    private final JsonMapper jsonMapper;

    public WaitJobHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String type() { return "WAIT"; }

    @Override
    public JsonNode execute(JsonNode payload, JobExecutionContext context) throws InterruptedException {
        long durationMs = payload.path("durationMs").asLong(1000);
        if (durationMs < 0 || durationMs > MAX_WAIT_MS) {
            throw new PermanentJobException("durationMs must be between 0 and " + MAX_WAIT_MS);
        }

        Thread.sleep(durationMs);
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("waitedMs", durationMs);
        result.put("completedAt", Instant.now().toString());
        return result;
    }
}
