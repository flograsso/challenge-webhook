package com.cobre.eventsApi.domain.model;

import java.time.Instant;

public record NotificationEvent(
        String eventId,
        String eventType,
        String content,
        DeliveryStatus deliveryStatus,
        Instant creationDate,
        Instant deliveryDate,
        String clientId,
        int retryCount,
        Integer httpStatusCode,
        String errorDetails
) {}
