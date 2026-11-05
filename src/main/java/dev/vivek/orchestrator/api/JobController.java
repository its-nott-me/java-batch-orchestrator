package dev.vivek.orchestrator.api;

import dev.vivek.orchestrator.application.JobService;
import dev.vivek.orchestrator.domain.JobStatus;
import dev.vivek.orchestrator.domain.JobSubmission;
import dev.vivek.orchestrator.handler.JobHandlerRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Validated
public class JobController {
    private final JobService jobService;
    private final JobHandlerRegistry handlerRegistry;

    public JobController(JobService jobService, JobHandlerRegistry handlerRegistry) {
        this.jobService = jobService;
        this.handlerRegistry = handlerRegistry;
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> submit(
            @Valid @RequestBody CreateJobRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        JobSubmission submission = jobService.submit(
                request.type(), request.payload(), request.maxAttempts(), request.priority(),
                request.runAt(), idempotencyKey
        );
        HttpStatus status = submission.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(JobResponse.from(submission.job()));
    }

    @GetMapping("/jobs/{id}")
    public JobResponse get(@PathVariable UUID id) {
        return JobResponse.from(jobService.get(id));
    }

    @GetMapping("/jobs")
    public List<JobResponse> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return jobService.list(status, limit).stream().map(JobResponse::from).toList();
    }

    @DeleteMapping("/jobs/{id}")
    public JobResponse cancel(@PathVariable UUID id) {
        return JobResponse.from(jobService.cancel(id));
    }

    @GetMapping("/job-types")
    public Map<String, List<String>> supportedTypes() {
        return Map.of("supportedTypes", handlerRegistry.supportedTypes());
    }
}
