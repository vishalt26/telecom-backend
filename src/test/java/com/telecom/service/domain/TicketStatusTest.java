package com.telecom.service.domain;

import org.junit.jupiter.api.Test;

import static com.telecom.service.domain.TicketStatus.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketStatusTest {

    @Test
    void followsTheForwardLifecycle() {
        assertTrue(PENDING.canMoveTo(IN_PROGRESS));
        assertTrue(IN_PROGRESS.canMoveTo(RESOLVED));
        assertTrue(RESOLVED.canMoveTo(COMPLETED));
    }

    @Test
    void resolvedTicketsCanBeReopened() {
        assertTrue(RESOLVED.canMoveTo(IN_PROGRESS));
    }

    @Test
    void rejectsSkippingAndBackwardsMoves() {
        assertFalse(PENDING.canMoveTo(RESOLVED));
        assertFalse(PENDING.canMoveTo(COMPLETED));
        assertFalse(IN_PROGRESS.canMoveTo(PENDING));
        assertFalse(IN_PROGRESS.canMoveTo(COMPLETED));
    }

    @Test
    void completedIsFinal() {
        for (TicketStatus s : values()) {
            assertFalse(COMPLETED.canMoveTo(s));
        }
    }
}
