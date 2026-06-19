# Notification Events API — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement three REST endpoints (`GET /notification_events`, `GET /notification_events/{id}`, `POST /notification_events/{id}/replay`) on a Spring Boot 4.1 / Java 26 app using hexagonal architecture, with a JSON file as storage and a real HTTP webhook call on replay.

**Architecture:** Hexagonal (ports & adapters). Domain defines pure interfaces (ports); application services implement use cases against those ports; adapters provide concrete implementations. No Spring annotations in domain or application layers.

**Tech Stack:** Spring Boot 4.1.0, Java 26 records, Jackson, RestClient (Spring 6 native), JUnit 5, Mockito, MockMvc.

---

## File Map

```
eventsApi/
├── build.gradle                                                        ← add spring-boot-starter-web
├── src/main/resources/application.properties                          ← add webhook URL + data path
└── src/main/java/com/cobre/eventsApi/
    ├── domain/
    │   ├── model/
    │   │   ├── DeliveryStatus.java                                    ← enum
    │   │   ├── NotificationEvent.java                                 ← record
    │   │   ├── ListNotificationEventsQuery.java                       ← record
    │   │   ├── PagedResult.java                                       ← record<T>
    │   │   └── WebhookResult.java                                     ← record
    │   ├── port/
    │   │   ├── in/
    │   │   │   ├── ListNotificationEventsUseCase.java                 ← interface
    │   │   │   ├── GetNotificationEventUseCase.java                   ← interface
    │   │   │   └── ReplayNotificationEventUseCase.java                ← interface
    │   │   └── out/
    │   │       ├── NotificationEventRepository.java                   ← interface
    │   │       └── WebhookDeliveryPort.java                           ← interface
    │   └── exception/
    │       ├── EventNotFoundException.java
    │       ├── EventAlreadyCompletedException.java
    │       ├── EventAlreadyInProgressException.java
    │       └── InvalidTokenException.java
    ├── application/
    │   ├── ListNotificationEventsService.java
    │   ├── GetNotificationEventService.java
    │   └── ReplayNotificationEventService.java
    ├── adapter/
    │   ├── in/rest/
    │   │   ├── NotificationEventController.java
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── JwtClientIdExtractor.java
    │   │   └── dto/
    │   │       ├── NotificationEventResponse.java
    │   │       ├── PagedResponse.java
    │   │       └── ErrorResponse.java
    │   └── out/
    │       ├── json/
    │       │   └── JsonNotificationEventRepository.java
    │       └── webhook/
    │           └── HttpWebhookDeliveryAdapter.java
    └── infrastructure/config/
        └── BeanConfig.java
```

---

## Task 1: Project Setup

**Files:**
- Modify: `build.gradle`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Add web dependency**

Replace the `dependencies` block in `build.gradle`:

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

- [ ] **Step 2: Configure application properties**

Replace `src/main/resources/application.properties`:

```properties
spring.application.name=eventsApi
webhook.delivery.url=https://webhook.site/9ec7ec61-3754-493e-8b9c-be8869b1905f
notification.events.data.path=data/notification_events.json
```

- [ ] **Step 3: Verify the app still compiles**

Run from the `eventsApi/` directory:
```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add eventsApi/build.gradle eventsApi/src/main/resources/application.properties
git commit -m "chore: add spring-boot-starter-web and configure app properties"
```

---

## Task 2: Domain Model

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/domain/model/DeliveryStatus.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/model/NotificationEvent.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/model/ListNotificationEventsQuery.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/model/PagedResult.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/model/WebhookResult.java`

- [ ] **Step 1: Create DeliveryStatus**

```java
// src/main/java/com/cobre/eventsApi/domain/model/DeliveryStatus.java
package com.cobre.eventsApi.domain.model;

public enum DeliveryStatus {
    PENDING, COMPLETED, FAILED, RETRYING
}
```

- [ ] **Step 2: Create NotificationEvent**

```java
// src/main/java/com/cobre/eventsApi/domain/model/NotificationEvent.java
package com.cobre.eventsApi.domain.model;

import java.time.Instant;

public record NotificationEvent(
        String eventId,
        String eventType,
        String content,
        DeliveryStatus deliveryStatus,
        Instant creationDate,
        Instant deliveryDate,
        String clientId,
        int retryCount,
        Integer httpStatusCode,
        String errorDetails
) {}
```

- [ ] **Step 3: Create ListNotificationEventsQuery**

```java
// src/main/java/com/cobre/eventsApi/domain/model/ListNotificationEventsQuery.java
package com.cobre.eventsApi.domain.model;

import java.time.Instant;
import java.util.List;

public record ListNotificationEventsQuery(
        String clientId,
        Instant dateFrom,
        Instant dateTo,
        List<DeliveryStatus> statuses,
        int page,
        int pageSize
) {}
```

- [ ] **Step 4: Create PagedResult**

```java
// src/main/java/com/cobre/eventsApi/domain/model/PagedResult.java
package com.cobre.eventsApi.domain.model;

import java.util.List;

public record PagedResult<T>(
        List<T> data,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {}
```

- [ ] **Step 5: Create WebhookResult**

```java
// src/main/java/com/cobre/eventsApi/domain/model/WebhookResult.java
package com.cobre.eventsApi.domain.model;

public record WebhookResult(
        boolean success,
        int httpStatusCode,
        String errorMessage
) {}
```

- [ ] **Step 6: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/domain/
git commit -m "feat: add domain model records and DeliveryStatus enum"
```

---

## Task 3: Domain Ports

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/domain/port/in/ListNotificationEventsUseCase.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/port/in/GetNotificationEventUseCase.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/port/in/ReplayNotificationEventUseCase.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/port/out/NotificationEventRepository.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/port/out/WebhookDeliveryPort.java`

- [ ] **Step 1: Create inbound ports**

```java
// src/main/java/com/cobre/eventsApi/domain/port/in/ListNotificationEventsUseCase.java
package com.cobre.eventsApi.domain.port.in;

import com.cobre.eventsApi.domain.model.ListNotificationEventsQuery;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.PagedResult;

public interface ListNotificationEventsUseCase {
    PagedResult<NotificationEvent> list(ListNotificationEventsQuery query);
}
```

```java
// src/main/java/com/cobre/eventsApi/domain/port/in/GetNotificationEventUseCase.java
package com.cobre.eventsApi.domain.port.in;

import com.cobre.eventsApi.domain.model.NotificationEvent;

public interface GetNotificationEventUseCase {
    NotificationEvent getById(String eventId, String clientId);
}
```

```java
// src/main/java/com/cobre/eventsApi/domain/port/in/ReplayNotificationEventUseCase.java
package com.cobre.eventsApi.domain.port.in;

import com.cobre.eventsApi.domain.model.NotificationEvent;

public interface ReplayNotificationEventUseCase {
    NotificationEvent replay(String eventId, String clientId);
}
```

- [ ] **Step 2: Create outbound ports**

```java
// src/main/java/com/cobre/eventsApi/domain/port/out/NotificationEventRepository.java
package com.cobre.eventsApi.domain.port.out;

import com.cobre.eventsApi.domain.model.NotificationEvent;

import java.util.List;
import java.util.Optional;

public interface NotificationEventRepository {
    List<NotificationEvent> findByClientId(String clientId);
    Optional<NotificationEvent> findByIdAndClientId(String eventId, String clientId);
    void save(NotificationEvent event);
}
```

```java
// src/main/java/com/cobre/eventsApi/domain/port/out/WebhookDeliveryPort.java
package com.cobre.eventsApi.domain.port.out;

import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.WebhookResult;

public interface WebhookDeliveryPort {
    WebhookResult deliver(NotificationEvent event);
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/domain/port/
git commit -m "feat: add domain ports (in/out interfaces)"
```

---

## Task 4: Domain Exceptions

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/domain/exception/EventNotFoundException.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/exception/EventAlreadyCompletedException.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/exception/EventAlreadyInProgressException.java`
- Create: `src/main/java/com/cobre/eventsApi/domain/exception/InvalidTokenException.java`

- [ ] **Step 1: Create all four exceptions**

```java
// src/main/java/com/cobre/eventsApi/domain/exception/EventNotFoundException.java
package com.cobre.eventsApi.domain.exception;

public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(String eventId) {
        super("Notification event '" + eventId + "' not found");
    }
}
```

```java
// src/main/java/com/cobre/eventsApi/domain/exception/EventAlreadyCompletedException.java
package com.cobre.eventsApi.domain.exception;

public class EventAlreadyCompletedException extends RuntimeException {
    public EventAlreadyCompletedException(String eventId) {
        super("Notification event '" + eventId + "' was already delivered successfully and cannot be replayed");
    }
}
```

```java
// src/main/java/com/cobre/eventsApi/domain/exception/EventAlreadyInProgressException.java
package com.cobre.eventsApi.domain.exception;

public class EventAlreadyInProgressException extends RuntimeException {
    public EventAlreadyInProgressException(String eventId, String status) {
        super("Notification event '" + eventId + "' is currently in " + status + " status. Wait for the current attempt to settle before replaying.");
    }
}
```

```java
// src/main/java/com/cobre/eventsApi/domain/exception/InvalidTokenException.java
package com.cobre.eventsApi.domain.exception;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/domain/exception/
git commit -m "feat: add domain exceptions for event and auth errors"
```

---

## Task 5: Application Services

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/application/ListNotificationEventsService.java`
- Create: `src/main/java/com/cobre/eventsApi/application/GetNotificationEventService.java`
- Create: `src/main/java/com/cobre/eventsApi/application/ReplayNotificationEventService.java`

- [ ] **Step 1: Create ListNotificationEventsService**

```java
// src/main/java/com/cobre/eventsApi/application/ListNotificationEventsService.java
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
```

- [ ] **Step 2: Create GetNotificationEventService**

```java
// src/main/java/com/cobre/eventsApi/application/GetNotificationEventService.java
package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.port.in.GetNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;

public class GetNotificationEventService implements GetNotificationEventUseCase {

    private final NotificationEventRepository repository;

    public GetNotificationEventService(NotificationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public NotificationEvent getById(String eventId, String clientId) {
        return repository.findByIdAndClientId(eventId, clientId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }
}
```

- [ ] **Step 3: Create ReplayNotificationEventService**

```java
// src/main/java/com/cobre/eventsApi/application/ReplayNotificationEventService.java
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
                Instant.now(),
                event.clientId(),
                event.retryCount() + 1,
                result.httpStatusCode(),
                result.success() ? null : result.errorMessage()
        );

        repository.save(updated);
        return updated;
    }
}
```

- [ ] **Step 4: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/application/
git commit -m "feat: add application services for list, get and replay use cases"
```

---

## Task 6: JSON Storage Adapter

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/adapter/out/json/JsonNotificationEventRepository.java`

The JSON at `data/notification_events.json` uses snake_case keys and lowercase status values. This adapter maps those to domain types at load time and holds them in memory. `save()` replaces the entry in the in-memory list.

**Note on path:** `data/notification_events.json` is resolved relative to the JVM working directory, which is `eventsApi/` when running via `./gradlew bootRun`.

- [ ] **Step 1: Create the adapter**

```java
// src/main/java/com/cobre/eventsApi/adapter/out/json/JsonNotificationEventRepository.java
package com.cobre.eventsApi.adapter.out.json;

import com.cobre.eventsApi.domain.model.DeliveryStatus;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class JsonNotificationEventRepository implements NotificationEventRepository {

    @Value("${notification.events.data.path}")
    private String dataPath;

    private final ObjectMapper objectMapper;
    private List<NotificationEvent> events = new ArrayList<>();

    public JsonNotificationEventRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() throws IOException {
        EventsWrapper wrapper = objectMapper.readValue(new File(dataPath), EventsWrapper.class);
        events = new ArrayList<>(wrapper.events().stream().map(this::toDomain).toList());
    }

    @Override
    public List<NotificationEvent> findByClientId(String clientId) {
        return events.stream()
                .filter(e -> e.clientId().equals(clientId))
                .toList();
    }

    @Override
    public Optional<NotificationEvent> findByIdAndClientId(String eventId, String clientId) {
        return events.stream()
                .filter(e -> e.eventId().equals(eventId) && e.clientId().equals(clientId))
                .findFirst();
    }

    @Override
    public void save(NotificationEvent event) {
        events.replaceAll(e -> e.eventId().equals(event.eventId()) ? event : e);
    }

    private NotificationEvent toDomain(EventDto dto) {
        Instant date = dto.deliveryDate() != null ? Instant.parse(dto.deliveryDate()) : Instant.now();
        return new NotificationEvent(
                dto.eventId(),
                dto.eventType(),
                dto.content(),
                DeliveryStatus.valueOf(dto.deliveryStatus().toUpperCase()),
                date,
                date,
                dto.clientId(),
                0,
                null,
                null
        );
    }

    record EventsWrapper(List<EventDto> events) {}

    record EventDto(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("content") String content,
            @JsonProperty("delivery_date") String deliveryDate,
            @JsonProperty("delivery_status") String deliveryStatus,
            @JsonProperty("client_id") String clientId
    ) {}
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/adapter/out/json/
git commit -m "feat: add JSON file storage adapter for notification events"
```

---

## Task 7: Webhook Adapter

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/adapter/out/webhook/HttpWebhookDeliveryAdapter.java`

Uses Spring's `RestClient` (available since Spring 6.1). POSTs the event as JSON to the configured URL. Never throws — returns a `WebhookResult` regardless of outcome.

- [ ] **Step 1: Create the adapter**

```java
// src/main/java/com/cobre/eventsApi/adapter/out/webhook/HttpWebhookDeliveryAdapter.java
package com.cobre.eventsApi.adapter.out.webhook;

import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.WebhookResult;
import com.cobre.eventsApi.domain.port.out.WebhookDeliveryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HttpWebhookDeliveryAdapter implements WebhookDeliveryPort {

    @Value("${webhook.delivery.url}")
    private String webhookUrl;

    private final RestClient restClient;

    public HttpWebhookDeliveryAdapter() {
        this.restClient = RestClient.create();
    }

    @Override
    public WebhookResult deliver(NotificationEvent event) {
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (req, res) -> {
                        throw new RestClientResponseException(
                                "Webhook returned " + res.getStatusCode().value(),
                                res.getStatusCode(),
                                res.getStatusText(),
                                res.getHeaders(),
                                null,
                                null
                        );
                    })
                    .toBodilessEntity();

            return new WebhookResult(true, response.getStatusCode().value(), null);
        } catch (RestClientResponseException e) {
            return new WebhookResult(false, e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            return new WebhookResult(false, 0, e.getMessage());
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/adapter/out/webhook/
git commit -m "feat: add HTTP webhook delivery adapter using RestClient"
```

---

## Task 8: REST DTOs and JWT Extractor

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/adapter/in/rest/dto/NotificationEventResponse.java`
- Create: `src/main/java/com/cobre/eventsApi/adapter/in/rest/dto/PagedResponse.java`
- Create: `src/main/java/com/cobre/eventsApi/adapter/in/rest/dto/ErrorResponse.java`
- Create: `src/main/java/com/cobre/eventsApi/adapter/in/rest/JwtClientIdExtractor.java`

- [ ] **Step 1: Create NotificationEventResponse**

```java
// src/main/java/com/cobre/eventsApi/adapter/in/rest/dto/NotificationEventResponse.java
package com.cobre.eventsApi.adapter.in.rest.dto;

import com.cobre.eventsApi.domain.model.DeliveryStatus;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationEventResponse(
        String eventId,
        String eventType,
        String content,
        DeliveryStatus deliveryStatus,
        Instant creationDate,
        Instant deliveryDate,
        int retryCount,
        Integer httpStatusCode,
        String errorDetails
) {
    public static NotificationEventResponse from(NotificationEvent event) {
        return new NotificationEventResponse(
                event.eventId(),
                event.eventType(),
                event.content(),
                event.deliveryStatus(),
                event.creationDate(),
                event.deliveryDate(),
                event.retryCount(),
                event.httpStatusCode(),
                event.errorDetails()
        );
    }
}
```

- [ ] **Step 2: Create PagedResponse**

```java
// src/main/java/com/cobre/eventsApi/adapter/in/rest/dto/PagedResponse.java
package com.cobre.eventsApi.adapter.in.rest.dto;

import com.cobre.eventsApi.domain.model.PagedResult;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
        List<T> data,
        PaginationMeta pagination
) {
    public record PaginationMeta(int page, int pageSize, long totalItems, int totalPages) {}

    public static <S, T> PagedResponse<T> from(PagedResult<S> result, Function<S, T> mapper) {
        return new PagedResponse<>(
                result.data().stream().map(mapper).toList(),
                new PaginationMeta(result.page(), result.pageSize(), result.totalItems(), result.totalPages())
        );
    }
}
```

- [ ] **Step 3: Create ErrorResponse**

```java
// src/main/java/com/cobre/eventsApi/adapter/in/rest/dto/ErrorResponse.java
package com.cobre.eventsApi.adapter.in.rest.dto;

import java.util.UUID;

public record ErrorResponse(String code, String message, String traceId) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, UUID.randomUUID().toString());
    }
}
```

- [ ] **Step 4: Create JwtClientIdExtractor**

Parses the JWT payload (middle segment, Base64URL-decoded) and extracts the `client_id` claim. No signature validation — intentional for this implementation (documented as a TODO).

```java
// src/main/java/com/cobre/eventsApi/adapter/in/rest/JwtClientIdExtractor.java
package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.domain.exception.InvalidTokenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Component
public class JwtClientIdExtractor {

    private final ObjectMapper objectMapper;

    public JwtClientIdExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extract(String authorizationHeader) {
        // TODO: add signature validation against Cobre's identity provider public key
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Bearer token is missing or has expired");
        }
        String token = authorizationHeader.substring(7);
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("Bearer token is malformed");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            Map<?, ?> claims = objectMapper.readValue(decoded, Map.class);
            Object clientId = claims.get("client_id");
            if (clientId == null) {
                throw new InvalidTokenException("Token does not contain a client_id claim");
            }
            return clientId.toString();
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("Bearer token payload could not be parsed");
        }
    }
}
```

- [ ] **Step 5: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/adapter/in/rest/
git commit -m "feat: add REST DTOs and JWT client_id extractor"
```

---

## Task 9: Global Exception Handler and Controller

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/adapter/in/rest/GlobalExceptionHandler.java`
- Create: `src/main/java/com/cobre/eventsApi/adapter/in/rest/NotificationEventController.java`

- [ ] **Step 1: Create GlobalExceptionHandler**

```java
// src/main/java/com/cobre/eventsApi/adapter/in/rest/GlobalExceptionHandler.java
package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.adapter.in.rest.dto.ErrorResponse;
import com.cobre.eventsApi.domain.exception.EventAlreadyCompletedException;
import com.cobre.eventsApi.domain.exception.EventAlreadyInProgressException;
import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.exception.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EventNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("EVENT_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(EventAlreadyCompletedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyCompleted(EventAlreadyCompletedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("EVENT_ALREADY_COMPLETED", e.getMessage()));
    }

    @ExceptionHandler(EventAlreadyInProgressException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyInProgress(EventAlreadyInProgressException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("EVENT_ALREADY_IN_PROGRESS", e.getMessage()));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ErrorResponse> handleDateParse(DateTimeParseException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PARAMETER", "Invalid date format. Use ISO 8601, e.g. 2024-03-15T00:00:00Z"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_PARAMETER", "Invalid value for parameter '" + e.getName() + "': " + e.getValue()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred. Please retry or contact support."));
    }
}
```

- [ ] **Step 2: Create NotificationEventController**

Date params are received as `String` and parsed to `Instant` to give a clear 400 on bad format (caught by `GlobalExceptionHandler`).

```java
// src/main/java/com/cobre/eventsApi/adapter/in/rest/NotificationEventController.java
package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.adapter.in.rest.dto.NotificationEventResponse;
import com.cobre.eventsApi.adapter.in.rest.dto.PagedResponse;
import com.cobre.eventsApi.domain.model.DeliveryStatus;
import com.cobre.eventsApi.domain.model.ListNotificationEventsQuery;
import com.cobre.eventsApi.domain.port.in.GetNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.in.ListNotificationEventsUseCase;
import com.cobre.eventsApi.domain.port.in.ReplayNotificationEventUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/notification_events")
public class NotificationEventController {

    private final ListNotificationEventsUseCase listUseCase;
    private final GetNotificationEventUseCase getUseCase;
    private final ReplayNotificationEventUseCase replayUseCase;
    private final JwtClientIdExtractor jwtExtractor;

    public NotificationEventController(
            ListNotificationEventsUseCase listUseCase,
            GetNotificationEventUseCase getUseCase,
            ReplayNotificationEventUseCase replayUseCase,
            JwtClientIdExtractor jwtExtractor) {
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.replayUseCase = replayUseCase;
        this.jwtExtractor = jwtExtractor;
    }

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationEventResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) List<DeliveryStatus> status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        String clientId = jwtExtractor.extract(authorization);
        Instant from = dateFrom != null ? Instant.parse(dateFrom) : null;
        Instant to = dateTo != null ? Instant.parse(dateTo) : null;

        var query = new ListNotificationEventsQuery(clientId, from, to, status, page, Math.min(pageSize, 100));
        var result = listUseCase.list(query);
        return ResponseEntity.ok(PagedResponse.from(result, NotificationEventResponse::from));
    }

    @GetMapping("/{notification_event_id}")
    public ResponseEntity<NotificationEventResponse> getById(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("notification_event_id") String eventId) {

        String clientId = jwtExtractor.extract(authorization);
        var event = getUseCase.getById(eventId, clientId);
        return ResponseEntity.ok(NotificationEventResponse.from(event));
    }

    @PostMapping("/{notification_event_id}/replay")
    public ResponseEntity<NotificationEventResponse> replay(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("notification_event_id") String eventId) {

        String clientId = jwtExtractor.extract(authorization);
        var event = replayUseCase.replay(eventId, clientId);
        return ResponseEntity.ok(NotificationEventResponse.from(event));
    }
}
```

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add eventsApi/src/main/java/com/cobre/eventsApi/adapter/in/rest/
git commit -m "feat: add REST controller and global exception handler"
```

---

## Task 10: Bean Configuration and Smoke Test

**Files:**
- Create: `src/main/java/com/cobre/eventsApi/infrastructure/config/BeanConfig.java`

- [ ] **Step 1: Create BeanConfig**

Wires application-layer services against the port interfaces. The adapter implementations (`JsonNotificationEventRepository`, `HttpWebhookDeliveryAdapter`) are Spring `@Component`s auto-discovered by Spring Boot.

```java
// src/main/java/com/cobre/eventsApi/infrastructure/config/BeanConfig.java
package com.cobre.eventsApi.infrastructure.config;

import com.cobre.eventsApi.application.GetNotificationEventService;
import com.cobre.eventsApi.application.ListNotificationEventsService;
import com.cobre.eventsApi.application.ReplayNotificationEventService;
import com.cobre.eventsApi.domain.port.in.GetNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.in.ListNotificationEventsUseCase;
import com.cobre.eventsApi.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import com.cobre.eventsApi.domain.port.out.WebhookDeliveryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public ListNotificationEventsUseCase listNotificationEventsUseCase(NotificationEventRepository repository) {
        return new ListNotificationEventsService(repository);
    }

    @Bean
    public GetNotificationEventUseCase getNotificationEventUseCase(NotificationEventRepository repository) {
        return new GetNotificationEventService(repository);
    }

    @Bean
    public ReplayNotificationEventUseCase replayNotificationEventUseCase(
            NotificationEventRepository repository,
            WebhookDeliveryPort webhookDeliveryPort) {
        return new ReplayNotificationEventService(repository, webhookDeliveryPort);
    }
}
```

- [ ] **Step 2: Build the full project**

```bash
./gradlew build -x test
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Start the app**

```bash
./gradlew bootRun
```
Expected output includes: `Started EventsApiApplication` on port 8080.

- [ ] **Step 4: Smoke test — list events for CLIENT001**

The fake JWT below has payload `{"client_id":"CLIENT001"}`. Our extractor parses it without validating the signature.

```bash
curl -s -H "Authorization: Bearer eyJhbGciOiJub25lIn0.eyJjbGllbnRfaWQiOiJDTElFTlQwMDEifQ." \
  http://localhost:8080/notification_events | python3 -m json.tool
```
Expected: JSON with `data` array containing events for CLIENT001 (EVT001, EVT002, EVT007, EVT010) and `pagination` object.

- [ ] **Step 5: Smoke test — get single event**

```bash
curl -s -H "Authorization: Bearer eyJhbGciOiJub25lIn0.eyJjbGllbnRfaWQiOiJDTElFTlQwMDMifQ." \
  http://localhost:8080/notification_events/EVT005 | python3 -m json.tool
```
Expected: JSON with `eventId: "EVT005"`, `deliveryStatus: "FAILED"` (CLIENT003 owns EVT005).

- [ ] **Step 6: Smoke test — replay a failed event**

```bash
curl -s -X POST \
  -H "Authorization: Bearer eyJhbGciOiJub25lIn0.eyJjbGllbnRfaWQiOiJDTElFTlQwMDMifQ." \
  http://localhost:8080/notification_events/EVT005/replay | python3 -m json.tool
```
Expected: JSON with updated `deliveryStatus` (`COMPLETED` or `FAILED` depending on webhook response) and `retryCount: 1`. Check webhook.site to confirm the POST was received.

- [ ] **Step 7: Smoke test — 401 on missing token**

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/notification_events
```
Expected: `401`

- [ ] **Step 8: Smoke test — 404 on wrong owner**

CLIENT001 does not own EVT003 (that belongs to CLIENT002).

```bash
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer eyJhbGciOiJub25lIn0.eyJjbGllbnRfaWQiOiJDTElFTlQwMDEifQ." \
  http://localhost:8080/notification_events/EVT003
```
Expected: `404`

- [ ] **Step 9: Stop the app and commit**

```bash
# Ctrl+C to stop bootRun
git add eventsApi/src/main/java/com/cobre/eventsApi/infrastructure/
git commit -m "feat: add BeanConfig — wires application services to ports"
```

---

## Task 11: Unit Tests — Application Services

**Files:**
- Create: `src/test/java/com/cobre/eventsApi/application/ListNotificationEventsServiceTest.java`
- Create: `src/test/java/com/cobre/eventsApi/application/ReplayNotificationEventServiceTest.java`

- [ ] **Step 1: Create ListNotificationEventsServiceTest**

```java
// src/test/java/com/cobre/eventsApi/application/ListNotificationEventsServiceTest.java
package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.model.*;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ListNotificationEventsServiceTest {

    private NotificationEventRepository repository;
    private ListNotificationEventsService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(NotificationEventRepository.class);
        service = new ListNotificationEventsService(repository);
    }

    private NotificationEvent event(String id, DeliveryStatus status, Instant date) {
        return new NotificationEvent(id, "type", "content", status, date, date, "client1", 0, null, null);
    }

    @Test
    void returns_all_events_for_client_with_no_filters() {
        var e1 = event("E1", DeliveryStatus.COMPLETED, Instant.parse("2024-03-15T09:00:00Z"));
        var e2 = event("E2", DeliveryStatus.FAILED, Instant.parse("2024-03-15T10:00:00Z"));
        when(repository.findByClientId("client1")).thenReturn(List.of(e1, e2));

        var result = service.list(new ListNotificationEventsQuery("client1", null, null, null, 1, 20));

        assertThat(result.data()).hasSize(2);
        assertThat(result.totalItems()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void filters_by_single_status() {
        var e1 = event("E1", DeliveryStatus.COMPLETED, Instant.parse("2024-03-15T09:00:00Z"));
        var e2 = event("E2", DeliveryStatus.FAILED, Instant.parse("2024-03-15T10:00:00Z"));
        when(repository.findByClientId("client1")).thenReturn(List.of(e1, e2));

        var result = service.list(new ListNotificationEventsQuery("client1", null, null, List.of(DeliveryStatus.FAILED), 1, 20));

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).eventId()).isEqualTo("E2");
    }

    @Test
    void filters_by_multiple_statuses() {
        var e1 = event("E1", DeliveryStatus.COMPLETED, Instant.parse("2024-03-15T09:00:00Z"));
        var e2 = event("E2", DeliveryStatus.FAILED, Instant.parse("2024-03-15T10:00:00Z"));
        var e3 = event("E3", DeliveryStatus.PENDING, Instant.parse("2024-03-15T11:00:00Z"));
        when(repository.findByClientId("client1")).thenReturn(List.of(e1, e2, e3));

        var result = service.list(new ListNotificationEventsQuery("client1", null, null, List.of(DeliveryStatus.FAILED, DeliveryStatus.PENDING), 1, 20));

        assertThat(result.data()).extracting(NotificationEvent::eventId).containsExactly("E2", "E3");
    }

    @Test
    void filters_by_date_range() {
        var e1 = event("E1", DeliveryStatus.COMPLETED, Instant.parse("2024-03-14T09:00:00Z"));
        var e2 = event("E2", DeliveryStatus.COMPLETED, Instant.parse("2024-03-15T09:00:00Z"));
        var e3 = event("E3", DeliveryStatus.COMPLETED, Instant.parse("2024-03-16T09:00:00Z"));
        when(repository.findByClientId("client1")).thenReturn(List.of(e1, e2, e3));

        var result = service.list(new ListNotificationEventsQuery(
                "client1",
                Instant.parse("2024-03-15T00:00:00Z"),
                Instant.parse("2024-03-15T23:59:59Z"),
                null, 1, 20));

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).eventId()).isEqualTo("E2");
    }

    @Test
    void paginates_correctly_on_second_page() {
        var events = List.of(
                event("E1", DeliveryStatus.COMPLETED, Instant.now()),
                event("E2", DeliveryStatus.COMPLETED, Instant.now()),
                event("E3", DeliveryStatus.COMPLETED, Instant.now())
        );
        when(repository.findByClientId("client1")).thenReturn(events);

        var result = service.list(new ListNotificationEventsQuery("client1", null, null, null, 2, 2));

        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).eventId()).isEqualTo("E3");
        assertThat(result.totalItems()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
    }

    @Test
    void returns_empty_result_when_no_events_match() {
        when(repository.findByClientId("client1")).thenReturn(List.of());

        var result = service.list(new ListNotificationEventsQuery("client1", null, null, null, 1, 20));

        assertThat(result.data()).isEmpty();
        assertThat(result.totalItems()).isZero();
        assertThat(result.totalPages()).isZero();
    }
}
```

- [ ] **Step 2: Create ReplayNotificationEventServiceTest**

```java
// src/test/java/com/cobre/eventsApi/application/ReplayNotificationEventServiceTest.java
package com.cobre.eventsApi.application;

import com.cobre.eventsApi.domain.exception.EventAlreadyCompletedException;
import com.cobre.eventsApi.domain.exception.EventAlreadyInProgressException;
import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.model.*;
import com.cobre.eventsApi.domain.port.out.NotificationEventRepository;
import com.cobre.eventsApi.domain.port.out.WebhookDeliveryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplayNotificationEventServiceTest {

    private NotificationEventRepository repository;
    private WebhookDeliveryPort webhookPort;
    private ReplayNotificationEventService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(NotificationEventRepository.class);
        webhookPort = Mockito.mock(WebhookDeliveryPort.class);
        service = new ReplayNotificationEventService(repository, webhookPort);
    }

    private NotificationEvent event(String id, DeliveryStatus status) {
        return new NotificationEvent(id, "type", "content", status,
                Instant.parse("2024-03-15T09:00:00Z"), Instant.parse("2024-03-15T09:30:00Z"),
                "client1", 0, null, null);
    }

    @Test
    void throws_not_found_when_event_does_not_exist() {
        when(repository.findByIdAndClientId("EVT1", "client1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay("EVT1", "client1"))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("EVT1");
    }

    @Test
    void throws_conflict_when_event_is_completed() {
        when(repository.findByIdAndClientId("EVT1", "client1"))
                .thenReturn(Optional.of(event("EVT1", DeliveryStatus.COMPLETED)));

        assertThatThrownBy(() -> service.replay("EVT1", "client1"))
                .isInstanceOf(EventAlreadyCompletedException.class);
    }

    @Test
    void throws_unprocessable_when_event_is_pending() {
        when(repository.findByIdAndClientId("EVT1", "client1"))
                .thenReturn(Optional.of(event("EVT1", DeliveryStatus.PENDING)));

        assertThatThrownBy(() -> service.replay("EVT1", "client1"))
                .isInstanceOf(EventAlreadyInProgressException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void throws_unprocessable_when_event_is_retrying() {
        when(repository.findByIdAndClientId("EVT1", "client1"))
                .thenReturn(Optional.of(event("EVT1", DeliveryStatus.RETRYING)));

        assertThatThrownBy(() -> service.replay("EVT1", "client1"))
                .isInstanceOf(EventAlreadyInProgressException.class)
                .hasMessageContaining("RETRYING");
    }

    @Test
    void marks_event_completed_on_successful_delivery() {
        when(repository.findByIdAndClientId("EVT1", "client1"))
                .thenReturn(Optional.of(event("EVT1", DeliveryStatus.FAILED)));
        when(webhookPort.deliver(any())).thenReturn(new WebhookResult(true, 200, null));

        NotificationEvent result = service.replay("EVT1", "client1");

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.errorDetails()).isNull();
        assertThat(result.retryCount()).isEqualTo(1);
        verify(repository).save(result);
    }

    @Test
    void marks_event_failed_on_unsuccessful_delivery() {
        when(repository.findByIdAndClientId("EVT1", "client1"))
                .thenReturn(Optional.of(event("EVT1", DeliveryStatus.FAILED)));
        when(webhookPort.deliver(any())).thenReturn(new WebhookResult(false, 503, "Service Unavailable"));

        NotificationEvent result = service.replay("EVT1", "client1");

        assertThat(result.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(result.httpStatusCode()).isEqualTo(503);
        assertThat(result.errorDetails()).isEqualTo("Service Unavailable");
        verify(repository).save(result);
    }
}
```

- [ ] **Step 3: Run unit tests**

```bash
./gradlew test --tests "com.cobre.eventsApi.application.*"
```
Expected: All 11 tests pass. `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add eventsApi/src/test/java/com/cobre/eventsApi/application/
git commit -m "test: add unit tests for list and replay application services"
```

---

## Task 12: Integration Tests — Controller

**Files:**
- Create: `src/test/java/com/cobre/eventsApi/adapter/in/rest/NotificationEventControllerTest.java`

`@WebMvcTest` loads only the web layer. `BeanConfig` is excluded via `excludeFilters` to avoid trying to instantiate non-web beans. All use cases and `JwtClientIdExtractor` are mocked.

- [ ] **Step 1: Create NotificationEventControllerTest**

```java
// src/test/java/com/cobre/eventsApi/adapter/in/rest/NotificationEventControllerTest.java
package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.domain.exception.EventAlreadyCompletedException;
import com.cobre.eventsApi.domain.exception.EventAlreadyInProgressException;
import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.exception.InvalidTokenException;
import com.cobre.eventsApi.domain.model.*;
import com.cobre.eventsApi.domain.port.in.GetNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.in.ListNotificationEventsUseCase;
import com.cobre.eventsApi.domain.port.in.ReplayNotificationEventUseCase;
import com.cobre.eventsApi.infrastructure.config.BeanConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = NotificationEventController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = BeanConfig.class)
)
class NotificationEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListNotificationEventsUseCase listUseCase;
    @MockitoBean
    private GetNotificationEventUseCase getUseCase;
    @MockitoBean
    private ReplayNotificationEventUseCase replayUseCase;
    @MockitoBean
    private JwtClientIdExtractor jwtExtractor;

    private static final String AUTH = "Bearer eyJhbGciOiJub25lIn0.eyJjbGllbnRfaWQiOiJDTElFTlQwMDEifQ.";
    private static final String CLIENT_ID = "CLIENT001";

    private NotificationEvent sampleEvent(DeliveryStatus status) {
        return new NotificationEvent(
                "EVT001", "credit_card_payment", "Payment $150",
                status,
                Instant.parse("2024-03-15T09:00:00Z"),
                Instant.parse("2024-03-15T09:30:22Z"),
                CLIENT_ID, 0, 200, null
        );
    }

    // ── GET /notification_events ──────────────────────────────────────────────

    @Test
    void list_returns_200_with_paged_response() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);
        var paged = new PagedResult<>(List.of(sampleEvent(DeliveryStatus.COMPLETED)), 1, 20, 1L, 1);
        when(listUseCase.list(any())).thenReturn(paged);

        mockMvc.perform(get("/notification_events").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].eventId").value("EVT001"))
                .andExpect(jsonPath("$.data[0].deliveryStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.pagination.page").value(1));
    }

    @Test
    void list_returns_401_when_auth_header_missing() throws Exception {
        when(jwtExtractor.extract(null)).thenThrow(new InvalidTokenException("Bearer token is missing or has expired"));

        mockMvc.perform(get("/notification_events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void list_returns_400_on_invalid_status_value() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);

        mockMvc.perform(get("/notification_events")
                        .header("Authorization", AUTH)
                        .param("status", "INVALID_VALUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    // ── GET /notification_events/{id} ─────────────────────────────────────────

    @Test
    void get_returns_200_with_event_detail() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);
        when(getUseCase.getById("EVT001", CLIENT_ID)).thenReturn(sampleEvent(DeliveryStatus.COMPLETED));

        mockMvc.perform(get("/notification_events/EVT001").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("EVT001"))
                .andExpect(jsonPath("$.eventType").value("credit_card_payment"));
    }

    @Test
    void get_returns_404_when_event_not_found() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);
        when(getUseCase.getById("EVT999", CLIENT_ID)).thenThrow(new EventNotFoundException("EVT999"));

        mockMvc.perform(get("/notification_events/EVT999").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ── POST /notification_events/{id}/replay ─────────────────────────────────

    @Test
    void replay_returns_200_on_successful_delivery() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);
        when(replayUseCase.replay("EVT003", CLIENT_ID)).thenReturn(sampleEvent(DeliveryStatus.COMPLETED));

        mockMvc.perform(post("/notification_events/EVT003/replay").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryStatus").value("COMPLETED"));
    }

    @Test
    void replay_returns_409_when_event_already_completed() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);
        when(replayUseCase.replay("EVT001", CLIENT_ID))
                .thenThrow(new EventAlreadyCompletedException("EVT001"));

        mockMvc.perform(post("/notification_events/EVT001/replay").header("Authorization", AUTH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_ALREADY_COMPLETED"));
    }

    @Test
    void replay_returns_422_when_event_in_progress() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);
        when(replayUseCase.replay("EVT005", CLIENT_ID))
                .thenThrow(new EventAlreadyInProgressException("EVT005", "RETRYING"));

        mockMvc.perform(post("/notification_events/EVT005/replay").header("Authorization", AUTH))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENT_ALREADY_IN_PROGRESS"));
    }

    @Test
    void replay_returns_404_when_event_not_found() throws Exception {
        when(jwtExtractor.extract(AUTH)).thenReturn(CLIENT_ID);
        when(replayUseCase.replay("EVT999", CLIENT_ID))
                .thenThrow(new EventNotFoundException("EVT999"));

        mockMvc.perform(post("/notification_events/EVT999/replay").header("Authorization", AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }
}
```

- [ ] **Step 2: Run all tests**

```bash
./gradlew test
```
Expected: All tests pass. `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add eventsApi/src/test/java/com/cobre/eventsApi/adapter/in/rest/
git commit -m "test: add WebMvcTest integration tests for NotificationEventController"
```
