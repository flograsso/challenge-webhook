package com.cobre.eventsApi.adapter.out.webhook;

import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.WebhookResult;
import com.cobre.eventsApi.domain.port.out.WebhookDeliveryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpWebhookDeliveryAdapter implements WebhookDeliveryPort {

    private final RestClient restClient;
    private final String webhookUrl;

    public HttpWebhookDeliveryAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${webhook.delivery.url}") String webhookUrl) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    @Override
    public WebhookResult deliver(NotificationEvent event) {
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(webhookUrl)
                    .body(event)
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> {}) // suppress error throwing for all statuses
                    .toBodilessEntity();

            int status = response.getStatusCode().value();
            boolean success = status >= 200 && status < 300;
            return new WebhookResult(success, status, success ? null : "HTTP " + status);
        } catch (Exception e) {
            return new WebhookResult(false, 0, e.getMessage());
        }
    }
}
