package com.cobre.eventsApi.domain.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String eventId) {
        super("Notification event '" + eventId + "' not found");
    }
}
