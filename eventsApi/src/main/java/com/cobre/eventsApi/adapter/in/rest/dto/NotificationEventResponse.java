package com.cobre.eventsApi.adapter.in.rest.dto;

import com.cobre.eventsApi.domain.model.DeliveryStatus;
import com.cobre.eventsApi.domain.model.NotificationEvent;

import java.time.Instant;

public record NotificationEventResponse(
        String eventId,
        String eventType,
        String content,
        DeliveryStatus deliveryStatus,
        Instant creationDate,
        Instant deliveryDate,
        int retryCount,
        Integer httpStatusCode,
        String errorDetails
) {
    public static NotificationEventResponse from(NotificationEvent event) {
        return new NotificationEventResponse(
                event.eventId(),
                event.eventType(),
                event.content(),
                event.deliveryStatus(),
                event.creationDate(),
                event.deliveryDate(),
                event.retryCount(),
                event.httpStatusCode(),
                event.errorDetails()
        );
    }
}
