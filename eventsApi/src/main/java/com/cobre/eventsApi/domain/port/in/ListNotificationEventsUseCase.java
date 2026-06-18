package com.cobre.eventsApi.domain.port.in;

import com.cobre.eventsApi.domain.model.ListNotificationEventsQuery;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.PagedResult;

public interface ListNotificationEventsUseCase {
    PagedResult<NotificationEvent> list(ListNotificationEventsQuery query);
}
