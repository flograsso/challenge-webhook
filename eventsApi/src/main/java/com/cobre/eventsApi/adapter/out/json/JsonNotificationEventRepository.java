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
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class JsonNotificationEventRepository implements NotificationEventRepository {

    private final String dataPath;
    private final ObjectMapper objectMapper;
    private List<NotificationEvent> events = new ArrayList<>();

    public JsonNotificationEventRepository(
            @Value("${notification.events.data.path}") String dataPath,
            ObjectMapper objectMapper) {
        this.dataPath = dataPath;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws Exception {
        ClassPathResource resource = new ClassPathResource(dataPath);
        try (InputStream is = resource.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode eventsNode = root.isArray() ? root : root.path("events");
            List<NotificationEvent> loaded = new ArrayList<>();
            for (JsonNode node : eventsNode) {
                loaded.add(map(node));
            }
            this.events = loaded;
        }
    }

    private NotificationEvent map(JsonNode node) {
        String deliveryDateStr = node.path("delivery_date").asText(null);
        Instant deliveryDate = (deliveryDateStr != null && !deliveryDateStr.isEmpty())
                ? Instant.parse(deliveryDateStr) : null;

        String statusStr = node.path("delivery_status").asText("PENDING");
        DeliveryStatus status = DeliveryStatus.valueOf(statusStr.toUpperCase());

        return new NotificationEvent(
                node.path("event_id").asText(),
                node.path("event_type").asText(),
                node.path("content").asText(),
                status,
                deliveryDate,   // creationDate — JSON has no creation_date, reuse delivery_date
                deliveryDate,   // deliveryDate
                node.path("client_id").asText(),
                node.path("retry_count").asInt(0),
                node.path("http_status_code").isNull() ? null : node.path("http_status_code").asInt(),
                node.path("error_details").isNull() ? null : node.path("error_details").asText()
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
