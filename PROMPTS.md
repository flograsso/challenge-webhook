# AI-Assisted Development — Prompt History

This document records the prompts used to design and implement this project with AI assistance.

## Model & Tools

| | |
|---|---|
| **Model** | Claude Sonnet 4.6 (`claude-sonnet-4-6`) |
| **Interface** | [Claude Code](https://claude.ai/code) — Anthropic's official CLI |
| **Plugin** | `claude-plugins-official/superpowers` v5.1.0 |

### Superpowers skills used

| Skill | Purpose |
|---|---|
| `superpowers:brainstorming` | Explored requirements, proposed architecture options, got user approval before writing a single line of code |
| `superpowers:writing-plans` | Produced the 12-task implementation plan with full code for each task |
| `superpowers:subagent-driven-development` | Dispatched a fresh subagent per task + two-stage review (spec compliance → code quality) after each one |

---

## Prompt History

### 1 — ERD Design

> Quiero comenzar un nuevo diseño de un feature, para comenzar lee el pdf para tener contexto del mismo. Quiero comenzar realizando un diagrama uml con plantUML modelando las relaciones de entidades. Mi idea es utilizar una base relacional con las siguientes tablas: `event_type`, `event_subscriptions`, `events_notifications`. Si encuentras mejoras, comentarios o sugerencias a mi diseño dímelas.

PlantUML ERD generated. AI suggested adding `retry_count`, `next_retry_at`, `http_status_code`, `error_details`, and `secret_key` fields, plus changing `delivery_status` enum to include `RETRYING`.

---

### 2 — Repository Initialization

> Quiero ir armando un readme con este diagrama, inicializa un nuevo repo git con un readme e incluye el pdf adjunto, el json y el diagrama.

Git repo initialized with README, ERD diagram, PDF spec, and sample JSON.

---

### 3 — ERD Image

> Adjunta una imagen del plantuml para que pueda visualizarse.

PlantUML rendered to `docs/erd.png` and embedded in README.

---

### 4 — OpenAPI Spec

> Ahora quiero continuar con el system design de Event notification self service API. Quiero poder definir el swagger de estos tres endpoints:
>
> - `GET /notification_events` — filters: `dateFrom`, `dateTo`, `status` (multi-value), pagination (`page`, `pageSize`). Auth: Bearer JWT. Returns paginated list.
> - `GET /notification_events/{id}` — single event. Auth: Bearer JWT.
> - `POST /notification_events/{id}/replay` — re-queue failed event. Auth: Bearer JWT. Returns 202.

OpenAPI 3.0.3 spec created at `docs/openapi.yaml`.

---

### 5 — Replay Synchronous Correction

> El post será síncrono y disparará la ejecución de evento por lo que no es necesario `ReplayRequest` y se debe enviar 200 en lugar de 202.

Replay endpoint corrected: removed request body, changed response from 202 to 200.

---

### 6 — Implementation Brainstorming

> `/superpowers:brainstorming` — Implementemos estos tres endpoints dentro del proyecto Java de esta carpeta. Quiero utilizar arquitectura hexagonal para su implementación, dado que no tendremos una DB real se debe utilizar el json de la carpeta `/data` como storage de lectura. Al utilizar hexagonal generar un port que permita abstraernos del storage permitiendo que el día de mañana se utilice una db real y no se deban modificar otros métodos. El endpoint de replay debe hacer un POST a una URL leída desde el `application.properties` de la app.

`superpowers:brainstorming` skill invoked. Three decision points presented:

**JWT handling** — three options proposed:
- A) Full validation with a secret key
- B) Spring Security integration
- **C) Parse payload without signature validation** ← chosen

**Test level** — three options proposed:
- A) Unit tests only
- **B) Unit tests + `@WebMvcTest` integration tests** ← chosen
- C) Full `@SpringBootTest` integration tests

**Architecture** — three options proposed:
- **A) Hexagonal with in-memory JSON** ← chosen
- B) Layered architecture
- C) Functional style

---

### 7 — Domain Design Approval

> Si, considera que los endpoints serán como definí en el readme. Alguno incluye paginación.

Domain model approved: `NotificationEvent` record, `DeliveryStatus` enum, `ListNotificationEventsQuery`, `PagedResult<T>`, `WebhookResult`.

---

### 8 — Webhook URL

> Ok, los tests en la implementación dejémoslos para el final. Esta es la URL de webhook: `https://webhook.site/9ec7ec61-3754-493e-8b9c-be8869b1905f`

Tests deferred to last tasks. Webhook URL set in `application.properties`.

---

### 9 — Spec Approval

> Esta OK.

Written design spec approved → `docs/superpowers/specs/2026-06-18-notification-events-api-design.md` committed.

---

### 10 — Execution Mode

> La 1. Conviene limpiar contexto antes?

`superpowers:subagent-driven-development` selected as execution mode. Context cleaned before starting implementation.

---

### 11 — Implementation (Tasks 1–12)

> SI

Full 12-task implementation executed via fresh subagent per task:

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Project setup (`build.gradle`, `application.properties`) | `f787fb4` |
| 2 | Domain model (records, enum) | `589e98c` |
| 3 | Domain ports (use case interfaces, repository, webhook port) | `a9f0ddc` |
| 4 | Domain exceptions | `d188118` |
| 5 | Application services (list, get, replay) | `b09a6b6` |
| 6 | JSON storage adapter (`@PostConstruct` load, `CopyOnWriteArrayList`) | `fd53002` |
| 7 | HTTP webhook adapter (`RestClient`) | `eb355f4` |
| 8 | REST DTOs + `JwtClientIdExtractor` | `b213b54` |
| 9 | `GlobalExceptionHandler` + `NotificationEventController` | `51e5d9f` |
| 10 | `BeanConfig` + smoke test (real HTTP to webhook.site) | `b51546d` |
| 11 | Unit tests (14 tests) | `ccbd943` |
| 12 | Integration tests (`@SpringBootTest` + MockMvc, 7 tests) | `ad55eee` |

Each task had a two-stage review: spec compliance first, then code quality. Issues caught by reviewers and fixed before moving to the next task.

**Notable fixes caught by code quality review:**
- `deliveryDate` was being set even on failed webhook delivery → fixed to `null` on failure
- `ArrayList` used as shared mutable state → replaced with `CopyOnWriteArrayList`
- `isNull()` on Jackson `MissingNode` returns `false` → fixed to `isMissingNode() || isNull()`
- JWT `split("\\.")` discards trailing empty parts → fixed to `split("\\.", -1)`
- `DateTimeParseException` was falling through to 500 → added explicit 400 handler
- Missing `RestClient.Builder` bean in Spring Boot 4.1.0 → added to `BeanConfig`
- `@WebMvcTest` removed in Spring Boot 4.1.0 → switched to `@SpringBootTest(webEnvironment = MOCK)`

---

### 12 — Remote & Repo Cleanup

> Puedes configurar este repo como remote y pushear todo allí: `https://github.com/flograsso/challenge-webhook`

Remote configured, all commits pushed.

> Quedó raro ya que para ingresar al readme hay que entrar a la carpeta eventsApi.

README moved to repo root, paths updated.

> Es posible mover todo el contenido de eventsApi al root?

All project files moved from `eventsApi/` subfolder to repo root.

---

## Final Test Results

```
22 tests — 0 failures — 0 skipped

ListNotificationEventsServiceTest  (7 tests)
ReplayNotificationEventServiceTest (7 tests)
NotificationEventControllerTest    (7 tests)
EventsApiApplicationTests          (1 smoke test)
```
