package com.cobre.eventsApi.domain.port.out;

import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.WebhookResult;

public interface WebhookDeliveryPort {
    WebhookResult deliver(NotificationEvent event);
}
