package com.telecom.service.domain;

public enum TicketType {
    COMPLAINT("Complaint"),
    SERVICE_REQUEST("Service request");

    private final String label;

    TicketType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
