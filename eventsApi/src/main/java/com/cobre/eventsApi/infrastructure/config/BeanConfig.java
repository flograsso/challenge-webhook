package com.cobre.eventsApi.infrastructure.config;

import com.cobre.eventsApi.adapter.out.json.JsonNotificationEventRepository;
import com.cobre.eventsApi.adapter.out.webhook.HttpWebhookDeliveryAdapter;
import com.cobre.eventsApi.application.GetNotificationEventService;
import com.cobre.eventsApi.application.ListNotificationEventsService;
import com.cobre.eventsApi.application.ReplayNotificationEventService;
import com.cobre.eventsApi.domain.port.in.GetNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.in.ListNotificationEventsUseCase;
import com.cobre.eventsApi.domain.port.in.ReplayNotificationEventUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class BeanConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public ListNotificationEventsUseCase listNotificationEventsUseCase(JsonNotificationEventRepository repository) {
        return new ListNotificationEventsService(repository);
    }

    @Bean
    public GetNotificationEventUseCase getNotificationEventUseCase(JsonNotificationEventRepository repository) {
        return new GetNotificationEventService(repository);
    }

    @Bean
    public ReplayNotificationEventUseCase replayNotificationEventUseCase(
            JsonNotificationEventRepository repository,
            HttpWebhookDeliveryAdapter webhookAdapter) {
        return new ReplayNotificationEventService(repository, webhookAdapter);
    }
}
