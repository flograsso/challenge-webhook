package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.model.ListNotificationEventsQuery;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.PagedResult;
import com.cobre.eventsApi.domain.port.in.ListNotificationEventsUseCase;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;

import java.util.List;

public class ListNotificationEventsService implements ListNotificationEventsUseCase {

    private final NotificationEventRepository repository;

    public ListNotificationEventsService(NotificationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public PagedResult<NotificationEvent> list(ListNotificationEventsQuery query) {
        List<NotificationEvent> filtered = repository.findByClientId(query.clientId())
                .stream()
                .filter(e -> query.dateFrom() == null || !e.creationDate().isBefore(query.dateFrom()))
                .filter(e -> query.dateTo() == null || !e.creationDate().isAfter(query.dateTo()))
                .filter(e -> query.statuses() == null || query.statuses().isEmpty() || query.statuses().contains(e.deliveryStatus()))
                .toList();

        long totalItems = filtered.size();
        int totalPages = (totalItems == 0) ? 0 : (int) Math.ceil((double) totalItems / query.pageSize());

        List<NotificationEvent> page = filtered.stream()
                .skip((long) (query.page() - 1) * query.pageSize())
                .limit(query.pageSize())
                .toList();

        return new PagedResult<>(page, query.page(), query.pageSize(), totalItems, totalPages);
    }
}
