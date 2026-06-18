# Design: Notification Events API

**Date:** 2026-06-18
**Scope:** Three REST endpoints for the self-service notification events API, implemented with hexagonal architecture on top of a JSON file as storage.

---

## Context

Spring Boot 4.1.0 / Java 26 project (`eventsApi/`). No real database — storage is `data/notification_events.json`. The design must abstract the storage behind a port so a future DB adapter can be plugged in without touching domain or application code.

Full API contract: `docs/openapi.yaml`

---

## Architecture: Hexagonal (Ports & Adapters)

```
com.cobre.eventsApi
├── domain/
│   ├── model/
│   │   ├── NotificationEvent.java       ← record
│   │   ├── DeliveryStatus.java          ← enum
│   │   ├── ListNotificationEventsQuery.java ← record (filter + pagination params)
│   │   ├── PagedResult.java             ← record (data + pagination metadata)
│   │   └── WebhookResult.java           ← record (httpStatusCode, errorMessage)
│   ├── port/
│   │   ├── in/
│   │   │   ├── ListNotificationEventsUseCase.java
│   │   │   ├── GetNotificationEventUseCase.java
│   │   │   └── ReplayNotificationEventUseCase.java
│   │   └── out/
│   │       ├── NotificationEventRepository.java
│   │       └── WebhookDeliveryPort.java
│   └── exception/
│       ├── EventNotFoundException.java
│       ├── EventAlreadyCompletedException.java
│       └── EventAlreadyInProgressException.java
├── application/
│   ├── ListNotificationEventsService.java
│   ├── GetNotificationEventService.java
│   └── ReplayNotificationEventService.java
├── adapter/
│   ├── in/
│   │   └── rest/
│   │       ├── NotificationEventController.java
│   │       ├── GlobalExceptionHandler.java
│   │       ├── JwtClientIdExtractor.java
│   │       └── dto/
│   │           ├── NotificationEventResponse.java
│   │           └── PagedResponse.java
│   └── out/
│       ├── json/
│       │   └── JsonNotificationEventRepository.java
│       └── webhook/
│           └── HttpWebhookDeliveryAdapter.java
└── infrastructure/
    └── config/
        └── BeanConfig.java
```

---

## Domain

### `NotificationEvent` (record)

| Field | Type | Notes |
|---|---|---|
| `eventId` | `String` | From JSON `event_id` |
| `eventType` | `String` | From JSON `event_type` |
| `content` | `String` | From JSON `content` |
| `deliveryStatus` | `DeliveryStatus` | Uppercased from JSON |
| `creationDate` | `Instant` | Derived from JSON `delivery_date` (JSON has no `creation_date`; treat `delivery_date` as creation for now) |
| `deliveryDate` | `Instant` | Nullable |
| `clientId` | `String` | From JSON `client_id` — internal only, never exposed in API responses |
| `retryCount` | `int` | Defaulted to 0 if absent from JSON |
| `httpStatusCode` | `Integer` | Nullable |
| `errorDetails` | `String` | Nullable |

### `DeliveryStatus` (enum)

`PENDING | COMPLETED | FAILED | RETRYING`

JSON values (`completed`, `failed`) are uppercased on load.

### `ListNotificationEventsQuery` (record)

```
clientId, dateFrom (Instant), dateTo (Instant), statuses (List<DeliveryStatus>), page (int), pageSize (int)
```

Defaults: `page=1`, `pageSize=20`. Maximum `pageSize=100`.

### `PagedResult<T>` (record)

```
List<T> data, int page, int pageSize, long totalItems, int totalPages
```

### `WebhookResult` (record)

```
boolean success, int httpStatusCode, String errorMessage
```

---

## Ports

### Inbound (use cases)

```java
// port/in/
PagedResult<NotificationEvent> ListNotificationEventsUseCase.list(ListNotificationEventsQuery query)
NotificationEvent               GetNotificationEventUseCase.getById(String eventId, String clientId)
NotificationEvent               ReplayNotificationEventUseCase.replay(String eventId, String clientId)
```

### Outbound

```java
// port/out/
List<NotificationEvent>         NotificationEventRepository.findByClientId(String clientId)
Optional<NotificationEvent>     NotificationEventRepository.findByIdAndClientId(String eventId, String clientId)
void                            NotificationEventRepository.save(NotificationEvent event)

WebhookResult                   WebhookDeliveryPort.deliver(NotificationEvent event)
```

---

## Application Services

### `ListNotificationEventsService`

1. `repository.findByClientId(query.clientId())`
2. Filter stream: `dateFrom` / `dateTo` against `creationDate` (both inclusive, both optional).
3. Filter stream: `statuses` if non-empty list provided.
4. Compute `totalItems` on filtered list.
5. Apply pagination: `skip((page-1) * pageSize).limit(pageSize)`.
6. Return `PagedResult`.

### `GetNotificationEventService`

1. `repository.findByIdAndClientId(eventId, clientId)`
2. `.orElseThrow(EventNotFoundException::new)`

### `ReplayNotificationEventService`

1. `repository.findByIdAndClientId(eventId, clientId)` → `EventNotFoundException` if empty.
2. `if COMPLETED` → throw `EventAlreadyCompletedException`.
3. `if PENDING || RETRYING` → throw `EventAlreadyInProgressException`.
4. `webhookDeliveryPort.deliver(event)` → `WebhookResult`.
5. Build updated event:
   - `deliveryStatus` = `COMPLETED` if `result.success()` else `FAILED`.
   - `deliveryDate` = `Instant.now()`.
   - `retryCount` = `event.retryCount() + 1`.
   - `httpStatusCode` = `result.httpStatusCode()`.
   - `errorDetails` = `result.errorMessage()` (null on success).
6. `repository.save(updatedEvent)`.
7. Return updated event.

---

## Adapters

### `JsonNotificationEventRepository`

- Injected `@Value("${notification.events.data.path}")` for the JSON path.
- Loads and deserializes the JSON on first access (lazy via `@PostConstruct` or field init).
- Holds events in a `List<NotificationEvent>` in memory.
- `save()` replaces the matching element in the list (in-memory only — no file write).

### `HttpWebhookDeliveryAdapter`

- `@Value("${webhook.delivery.url}")` → `https://webhook.site/9ec7ec61-3754-493e-8b9c-be8869b1905f`
- Uses `RestClient` (Spring 6 native) to POST the event payload as JSON.
- On HTTP error or network exception: returns `WebhookResult(false, statusCode, message)`.
- Never throws — domain services receive only `WebhookResult`.

### `NotificationEventController`

All endpoints extract `client_id` via `JwtClientIdExtractor` before delegating to the use case.

| Endpoint | Use case called | Success response |
|---|---|---|
| `GET /notification_events` | `ListNotificationEventsUseCase` | `200` + `PagedResponse<NotificationEventResponse>` |
| `GET /notification_events/{id}` | `GetNotificationEventUseCase` | `200` + `NotificationEventResponse` |
| `POST /notification_events/{id}/replay` | `ReplayNotificationEventUseCase` | `200` + `NotificationEventResponse` |

Query params for `GET /notification_events`: `dateFrom`, `dateTo`, `status` (multi-value), `page`, `pageSize`.

### `JwtClientIdExtractor`

```
Authorization: Bearer <header>.<payload>.<signature>
```

1. Split by `.`, take index 1 (payload).
2. Base64URL-decode.
3. Parse JSON with Jackson, read `client_id` claim.
4. If header absent, malformed, or claim missing → throw `InvalidTokenException`.

### `NotificationEventResponse` (DTO)

Maps `NotificationEvent` to the API response shape. **Excludes `clientId`** (internal field, never returned).

### `GlobalExceptionHandler` (`@ControllerAdvice`)

| Exception | HTTP | `code` field |
|---|---|---|
| `InvalidTokenException` | 401 | `UNAUTHORIZED` |
| `EventNotFoundException` | 404 | `EVENT_NOT_FOUND` |
| `EventAlreadyCompletedException` | 409 | `EVENT_ALREADY_COMPLETED` |
| `EventAlreadyInProgressException` | 422 | `EVENT_ALREADY_IN_PROGRESS` |
| Any other `Exception` | 500 | `INTERNAL_ERROR` |

All errors follow the `ErrorResponse` schema from the OpenAPI spec: `{ code, message, traceId }`.

---

## Configuration

### `build.gradle` additions

```groovy
implementation 'org.springframework.boot:spring-boot-starter-web'
```

Jackson is transitive. No Lombok needed (Java records used throughout).

### `application.properties`

```properties
spring.application.name=eventsApi
webhook.delivery.url=https://webhook.site/9ec7ec61-3754-493e-8b9c-be8869b1905f
notification.events.data.path=data/notification_events.json
```

### `BeanConfig`

Wires use case interfaces to service implementations via explicit `@Bean` methods. Services are plain Java classes (no `@Service` annotation) — this keeps them framework-free and easy to unit test.

---

## Tests (deferred — implemented last)

### Unit tests (`application/`)

- `ListNotificationEventsServiceTest`: filtering by date range, filtering by status, pagination, empty results, multiple statuses.
- `ReplayNotificationEventServiceTest`: 404 path, 409 path, 422 path, successful delivery (COMPLETED), failed delivery (FAILED).

### Integration tests (`@WebMvcTest`)

- `NotificationEventControllerTest`: one test per success path + one per error code (401, 404, 409, 422, 400 bad params).
- Use cases mocked with `@MockBean`.

---

## Out of scope

- JWT signature validation (intentionally omitted; documented as a TODO in `JwtClientIdExtractor`).
- Persistence to disk of replay results (in-memory only).
- Rate limiting, CORS configuration.
