package com.cobre.eventsApi.domain.port.in;

import com.cobre.eventsApi.domain.model.NotificationEvent;

public interface GetNotificationEventUseCase {
    NotificationEvent getById(String eventId, String clientId);
}
