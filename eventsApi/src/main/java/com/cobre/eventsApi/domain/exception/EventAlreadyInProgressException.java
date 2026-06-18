package com.cobre.eventsApi.domain.exception;

public class EventAlreadyInProgressException extends RuntimeException {
    public EventAlreadyInProgressException(String eventId, String status) {
        super("Notification event '" + eventId + "' is currently in " + status + " status. Wait for the current attempt to settle before replaying.");
    }
}
