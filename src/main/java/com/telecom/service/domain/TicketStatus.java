package com.telecom.service.domain;

public enum TicketStatus {
    PENDING("Pending"),
    IN_PROGRESS("In progress"),
    RESOLVED("Resolved"),
    COMPLETED("Completed");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Allowed lifecycle: Pending -> In progress -> Resolved -> Completed (a resolved ticket can be reopened). */
    public boolean canMoveTo(TicketStatus next) {
        return switch (this) {
            case PENDING -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == RESOLVED;
            case RESOLVED -> next == COMPLETED || next == IN_PROGRESS;
            case COMPLETED -> false;
        };
    }
}
