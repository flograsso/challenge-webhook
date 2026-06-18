package com.cobre.eventsApi.domain.port.in;

import com.cobre.eventsApi.domain.model.NotificationEvent;

public interface ReplayNotificationEventUseCase {
    NotificationEvent replay(String eventId, String clientId);
}
