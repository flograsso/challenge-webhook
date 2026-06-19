package com.cobre.eventsApi.domain.exception;

public class EventAlreadyCompletedException extends RuntimeException {
    public EventAlreadyCompletedException(String eventId) {
        super("Notification event '" + eventId + "' was already delivered successfully and cannot be replayed");
    }
}
