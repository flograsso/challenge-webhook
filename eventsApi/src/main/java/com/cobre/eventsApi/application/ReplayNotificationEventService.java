package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.exception.EventAlreadyCompletedException;
import com.cobre.eventsApi.domain.exception.EventAlreadyInProgressException;
import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.model.DeliveryStatus;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.WebhookResult;
import com.cobre.eventsApi.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import com.cobre.eventsApi.domain.port.out.WebhookDeliveryPort;

import java.time.Instant;

public class ReplayNotificationEventService implements ReplayNotificationEventUseCase {

    private final NotificationEventRepository repository;
    private final WebhookDeliveryPort webhookDeliveryPort;

    public ReplayNotificationEventService(NotificationEventRepository repository, WebhookDeliveryPort webhookDeliveryPort) {
        this.repository = repository;
        this.webhookDeliveryPort = webhookDeliveryPort;
    }

    @Override
    public NotificationEvent replay(String eventId, String clientId) {
        NotificationEvent event = repository.findByIdAndClientId(eventId, clientId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.deliveryStatus() == DeliveryStatus.COMPLETED) {
            throw new EventAlreadyCompletedException(eventId);
        }
        if (event.deliveryStatus() == DeliveryStatus.PENDING || event.deliveryStatus() == DeliveryStatus.RETRYING) {
            throw new EventAlreadyInProgressException(eventId, event.deliveryStatus().name());
        }

        WebhookResult result = webhookDeliveryPort.deliver(event);

        NotificationEvent updated = new NotificationEvent(
                event.eventId(),
                event.eventType(),
                event.content(),
                result.success() ? DeliveryStatus.COMPLETED : DeliveryStatus.FAILED,
                event.creationDate(),
                result.success() ? Instant.now() : null,
                event.clientId(),
                event.retryCount() + 1,
                result.httpStatusCode(),
                result.success() ? null : result.errorMessage()
        );

        repository.save(updated);
        return updated;
    }
}
