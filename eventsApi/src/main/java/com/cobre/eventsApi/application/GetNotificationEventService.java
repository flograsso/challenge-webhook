package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.port.in.GetNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;

public class GetNotificationEventService implements GetNotificationEventUseCase {

    private final NotificationEventRepository repository;

    public GetNotificationEventService(NotificationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationEvent getById(String eventId, String clientId) {
        return repository.findByIdAndClientId(eventId, clientId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
