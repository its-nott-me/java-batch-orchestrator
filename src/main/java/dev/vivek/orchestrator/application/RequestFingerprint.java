package dev.vivek.orchestrator.application;

import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/** A compact fingerprint used to detect accidental idempotency-key reuse. */
public final class RequestFingerprint {
    private RequestFingerprint() { }

    public static String sha256(String type, JsonNode payload, int maxAttempts, int priority, OffsetDateTime runAt) {
        String normalizedRunAt = runAt == null ? "<immediate>" : runAt.toInstant().toString();
        String canonicalRequest = type + "\n" + payload.toString() + "\n" + maxAttempts
                + "\n" + priority + "\n" + normalizedRunAt;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime", impossible);
        }
    }
}
