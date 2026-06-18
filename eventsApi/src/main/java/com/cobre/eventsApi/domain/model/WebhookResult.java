package com.cobre.eventsApi.domain.model;

public record WebhookResult(
        boolean success,
        int httpStatusCode,
        String errorMessage
) {}
