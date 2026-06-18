package com.cobre.eventsApi.domain.model;

import java.time.Instant;
import java.util.List;

public record ListNotificationEventsQuery(
        String clientId,
        Instant dateFrom,
        Instant dateTo,
        List<DeliveryStatus> statuses,
        int page,
        int pageSize
) {}
