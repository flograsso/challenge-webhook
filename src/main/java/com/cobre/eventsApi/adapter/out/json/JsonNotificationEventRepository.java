package com.cobre.eventsApi.adapter.out.json;

import com.cobre.eventsApi.domain.model.DeliveryStatus;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class JsonNotificationEventRepository implements NotificationEventRepository {

    private final String dataPath;
    private final ObjectMapper objectMapper;
    private final List<NotificationEvent> events = new CopyOnWriteArrayList<>();

    public JsonNotificationEventRepository(
            @Value("${notification.events.data.path}") String dataPath,
            ObjectMapper objectMapper) {
        this.dataPath = dataPath;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        ClassPathResource resource = new ClassPathResource(dataPath);
        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode eventsNode = root.isArray() ? root : root.path("events");
            for (JsonNode node : eventsNode) {
                events.add(map(node));
            }
        }
    }

    private NotificationEvent map(JsonNode node) {
        String deliveryDateStr = node.path("delivery_date").asText(null);
        Instant deliveryDate = (deliveryDateStr != null && !deliveryDateStr.isEmpty())
                ? Instant.parse(deliveryDateStr) : null;

        String statusStr = node.path("delivery_status").asText("PENDING");
        DeliveryStatus status;
        try {
            status = DeliveryStatus.valueOf(statusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Unknown delivery_status '%s' for event '%s'".formatted(statusStr, node.path("event_id").asText()), e);
        }

        JsonNode httpNode = node.path("http_status_code");
        Integer httpStatusCode = (!httpNode.isMissingNode() && !httpNode.isNull()) ? httpNode.asInt() : null;

        JsonNode errNode = node.path("error_details");
        String errorDetails = (!errNode.isMissingNode() && !errNode.isNull()) ? errNode.asText() : null;

        return new NotificationEvent(
                node.path("event_id").asText(),
                node.path("event_type").asText(),
                node.path("content").asText(),
                status,
                deliveryDate,   // creationDate — JSON has no creation_date, reuse delivery_date
                deliveryDate,   // deliveryDate
                node.path("client_id").asText(),
                node.path("retry_count").asInt(0),
                httpStatusCode,
                errorDetails
        );
    }

    @Override
    public List<NotificationEvent> findByClientId(String clientId) {
        return events.stream()
                .filter(e -> clientId.equals(e.clientId()))
                .toList();
    }

    @Override
    public Optional<NotificationEvent> findByIdAndClientId(String eventId, String clientId) {
        return events.stream()
                .filter(e -> eventId.equals(e.eventId()) && clientId.equals(e.clientId()))
                .findFirst();
    }

    @Override
    public void save(NotificationEvent event) {
        events.replaceAll(e -> e.eventId().equals(event.eventId()) ? event : e);
    }
}
