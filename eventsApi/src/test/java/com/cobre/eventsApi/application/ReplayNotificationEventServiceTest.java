package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.exception.EventAlreadyCompletedException;
import com.cobre.eventsApi.domain.exception.EventAlreadyInProgressException;
import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.model.*;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import com.cobre.eventsApi.domain.port.out.WebhookDeliveryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplayNotificationEventServiceTest {

    @Mock
    NotificationEventRepository repository;
    @Mock
    WebhookDeliveryPort webhookPort;

    ReplayNotificationEventService service;

    private NotificationEvent event(String id, DeliveryStatus status) {
        return new NotificationEvent(id, "type", "content", status,
                Instant.parse("2024-01-01T00:00:00Z"), null, "CLIENT001", 0, null, null);
    }

    @BeforeEach
    void setUp() {
        service = new ReplayNotificationEventService(repository, webhookPort);
    }

    @Test
    void throwsEventNotFoundWhenEventDoesNotExist() {
        when(repository.findByIdAndClientId("MISSING", "CLIENT001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay("MISSING", "CLIENT001"))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void throwsAlreadyCompletedForCompletedEvent() {
        when(repository.findByIdAndClientId("E1", "CLIENT001"))
                .thenReturn(Optional.of(event("E1", DeliveryStatus.COMPLETED)));

        assertThatThrownBy(() -> service.replay("E1", "CLIENT001"))
                .isInstanceOf(EventAlreadyCompletedException.class);
        verifyNoInteractions(webhookPort);
    }

    @Test
    void throwsAlreadyInProgressForPendingEvent() {
        when(repository.findByIdAndClientId("E1", "CLIENT001"))
                .thenReturn(Optional.of(event("E1", DeliveryStatus.PENDING)));

        assertThatThrownBy(() -> service.replay("E1", "CLIENT001"))
                .isInstanceOf(EventAlreadyInProgressException.class);
        verifyNoInteractions(webhookPort);
    }

    @Test
    void throwsAlreadyInProgressForRetryingEvent() {
        when(repository.findByIdAndClientId("E1", "CLIENT001"))
                .thenReturn(Optional.of(event("E1", DeliveryStatus.RETRYING)));

        assertThatThrownBy(() -> service.replay("E1", "CLIENT001"))
                .isInstanceOf(EventAlreadyInProgressException.class);
        verifyNoInteractions(webhookPort);
    }

    @Test
    void setsCompletedStatusOnSuccessfulDelivery() {
        NotificationEvent failedEvent = event("E1", DeliveryStatus.FAILED);
        when(repository.findByIdAndClientId("E1", "CLIENT001")).thenReturn(Optional.of(failedEvent));
        when(webhookPort.deliver(any())).thenReturn(new WebhookResult(true, 200, null));

        NotificationEvent result = service.replay("E1", "CLIENT001");

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.errorDetails()).isNull();
        assertThat(result.deliveryDate()).isNotNull();
        assertThat(result.retryCount()).isEqualTo(1);
    }

    @Test
    void setsFailedStatusOnFailedDelivery() {
        NotificationEvent failedEvent = event("E1", DeliveryStatus.FAILED);
        when(repository.findByIdAndClientId("E1", "CLIENT001")).thenReturn(Optional.of(failedEvent));
        when(webhookPort.deliver(any())).thenReturn(new WebhookResult(false, 503, "Service Unavailable"));

        NotificationEvent result = service.replay("E1", "CLIENT001");

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.httpStatusCode()).isEqualTo(503);
        assertThat(result.errorDetails()).isEqualTo("Service Unavailable");
        assertThat(result.deliveryDate()).isNull();
        assertThat(result.retryCount()).isEqualTo(1);
    }

    @Test
    void savesUpdatedEventAfterReplay() {
        NotificationEvent failedEvent = event("E1", DeliveryStatus.FAILED);
        when(repository.findByIdAndClientId("E1", "CLIENT001")).thenReturn(Optional.of(failedEvent));
        when(webhookPort.deliver(any())).thenReturn(new WebhookResult(true, 200, null));

        service.replay("E1", "CLIENT001");

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().deliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
    }
}
