package dev.vivek.orchestrator.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record CreateJobRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$", message = "type must be an uppercase identifier")
        String type,
        @NotNull JsonNode payload,
        @Min(1) @Max(10) Integer maxAttempts,
        @Min(-10) @Max(10) Integer priority,
        OffsetDateTime runAt
) { }
