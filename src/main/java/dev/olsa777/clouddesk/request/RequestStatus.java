package dev.olsa777.clouddesk.request;

public enum RequestStatus {
    NEW, APPROVED, REJECTED, PROVISIONED;

    public boolean canTransitionTo(RequestStatus next) {
        return (this == NEW && (next == APPROVED || next == REJECTED))
                || (this == APPROVED && next == PROVISIONED);
    }

    public boolean reservesResources() {
        return this == APPROVED || this == PROVISIONED;
    }
}
