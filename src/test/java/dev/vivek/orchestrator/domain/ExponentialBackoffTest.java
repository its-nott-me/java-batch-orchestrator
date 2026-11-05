package dev.vivek.orchestrator.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExponentialBackoffTest {
    @Test
    void doublesDelayAndRespectsCap() {
        assertEquals(2, ExponentialBackoff.delaySeconds(1, 2, 30));
        assertEquals(4, ExponentialBackoff.delaySeconds(2, 2, 30));
        assertEquals(8, ExponentialBackoff.delaySeconds(3, 2, 30));
        assertEquals(16, ExponentialBackoff.delaySeconds(4, 2, 30));
        assertEquals(30, ExponentialBackoff.delaySeconds(5, 2, 30));
        assertEquals(30, ExponentialBackoff.delaySeconds(10, 2, 30));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> ExponentialBackoff.delaySeconds(0, 2, 30));
        assertThrows(IllegalArgumentException.class, () -> ExponentialBackoff.delaySeconds(1, 0, 30));
    }
}
