package com.cobre.eventsApi.domain.port.out;

import com.cobre.eventsApi.domain.model.NotificationEvent;

import java.util.List;
import java.util.Optional;

public interface NotificationEventRepository {
    List<NotificationEvent> findByClientId(String clientId);
    Optional<NotificationEvent> findByIdAndClientId(String eventId, String clientId);
    void save(NotificationEvent event);
}
