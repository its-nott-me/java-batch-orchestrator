package dev.vivek.orchestrator.handler;

import tools.jackson.databind.JsonNode;

public interface JobHandler {
    String type();
    JsonNode execute(JsonNode payload, JobExecutionContext context) throws Exception;
}
