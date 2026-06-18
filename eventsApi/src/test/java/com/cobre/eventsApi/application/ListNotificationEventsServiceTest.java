package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.model.*;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListNotificationEventsServiceTest {

    @Mock
    NotificationEventRepository repository;

    ListNotificationEventsService service;

    // Helper: create a NotificationEvent for testing
    private NotificationEvent event(String id, DeliveryStatus status, Instant creationDate) {
        return new NotificationEvent(id, "type", "content", status,
                creationDate, null, "CLIENT001", 0, null, null);
    }

    @BeforeEach
    void setUp() {
        service = new ListNotificationEventsService(repository);
    }

    @Test
    void returnsAllEventsForClientWhenNoFilters() {
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-01-02T00:00:00Z");
        List<NotificationEvent> events = List.of(
                event("E1", DeliveryStatus.COMPLETED, t1),
                event("E2", DeliveryStatus.FAILED, t2));
        when(repository.findByClientId("CLIENT001")).thenReturn(events);

        PagedResult<NotificationEvent> result = service.list(
                new ListNotificationEventsQuery("CLIENT001", null, null, null, 1, 20));

        assertThat(result.data()).hasSize(2);
        assertThat(result.totalItems()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void filtersEventsByStatus() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<NotificationEvent> events = List.of(
                event("E1", DeliveryStatus.COMPLETED, t),
                event("E2", DeliveryStatus.FAILED, t),
                event("E3", DeliveryStatus.COMPLETED, t));
        when(repository.findByClientId("CLIENT001")).thenReturn(events);

        PagedResult<NotificationEvent> result = service.list(
                new ListNotificationEventsQuery("CLIENT001", null, null,
                        List.of(DeliveryStatus.COMPLETED), 1, 20));

        assertThat(result.data()).hasSize(2)
                .allMatch(e -> e.deliveryStatus() == DeliveryStatus.COMPLETED);
    }

    @Test
    void filtersEventsByDateFrom() {
        Instant early = Instant.parse("2024-01-01T00:00:00Z");
        Instant late  = Instant.parse("2024-01-10T00:00:00Z");
        List<NotificationEvent> events = List.of(
                event("E1", DeliveryStatus.COMPLETED, early),
                event("E2", DeliveryStatus.COMPLETED, late));
        when(repository.findByClientId("CLIENT001")).thenReturn(events);

        Instant boundary = Instant.parse("2024-01-05T00:00:00Z");
        PagedResult<NotificationEvent> result = service.list(
                new ListNotificationEventsQuery("CLIENT001", boundary, null, null, 1, 20));

        assertThat(result.data()).hasSize(1)
                .first().extracting(NotificationEvent::eventId).isEqualTo("E2");
    }

    @Test
    void filtersEventsByDateTo() {
        Instant early = Instant.parse("2024-01-01T00:00:00Z");
        Instant late  = Instant.parse("2024-01-10T00:00:00Z");
        List<NotificationEvent> events = List.of(
                event("E1", DeliveryStatus.COMPLETED, early),
                event("E2", DeliveryStatus.COMPLETED, late));
        when(repository.findByClientId("CLIENT001")).thenReturn(events);

        Instant boundary = Instant.parse("2024-01-05T00:00:00Z");
        PagedResult<NotificationEvent> result = service.list(
                new ListNotificationEventsQuery("CLIENT001", null, boundary, null, 1, 20));

        assertThat(result.data()).hasSize(1)
                .first().extracting(NotificationEvent::eventId).isEqualTo("E1");
    }

    @Test
    void paginatesCorrectly() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<NotificationEvent> events = List.of(
                event("E1", DeliveryStatus.COMPLETED, t),
                event("E2", DeliveryStatus.COMPLETED, t),
                event("E3", DeliveryStatus.COMPLETED, t));
        when(repository.findByClientId("CLIENT001")).thenReturn(events);

        PagedResult<NotificationEvent> page1 = service.list(
                new ListNotificationEventsQuery("CLIENT001", null, null, null, 1, 2));
        PagedResult<NotificationEvent> page2 = service.list(
                new ListNotificationEventsQuery("CLIENT001", null, null, null, 2, 2));

        assertThat(page1.data()).hasSize(2);
        assertThat(page2.data()).hasSize(1);
        assertThat(page1.totalItems()).isEqualTo(3);
        assertThat(page1.totalPages()).isEqualTo(2);
    }

    @Test
    void returnsEmptyResultWhenNoEvents() {
        when(repository.findByClientId("CLIENT001")).thenReturn(List.of());

        PagedResult<NotificationEvent> result = service.list(
                new ListNotificationEventsQuery("CLIENT001", null, null, null, 1, 20));

        assertThat(result.data()).isEmpty();
        assertThat(result.totalItems()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void filtersMultipleStatuses() {
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<NotificationEvent> events = List.of(
                event("E1", DeliveryStatus.COMPLETED, t),
                event("E2", DeliveryStatus.FAILED, t),
                event("E3", DeliveryStatus.PENDING, t));
        when(repository.findByClientId("CLIENT001")).thenReturn(events);

        PagedResult<NotificationEvent> result = service.list(
                new ListNotificationEventsQuery("CLIENT001", null, null,
                        List.of(DeliveryStatus.COMPLETED, DeliveryStatus.FAILED), 1, 20));

        assertThat(result.data()).hasSize(2)
                .map(NotificationEvent::eventId).containsExactlyInAnyOrder("E1", "E2");
    }
}
