package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.adapter.in.rest.dto.NotificationEventResponse;
import com.cobre.eventsApi.adapter.in.rest.dto.PagedResponse;
import com.cobre.eventsApi.domain.model.DeliveryStatus;
import com.cobre.eventsApi.domain.model.ListNotificationEventsQuery;
import com.cobre.eventsApi.domain.model.NotificationEvent;
import com.cobre.eventsApi.domain.model.PagedResult;
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
    public ResponseEntity<PagedResponse<NotificationEventResponse>> listEvents(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(name = "status", required = false) List<String> statuses,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        String clientId = jwtExtractor.extract(authHeader);

        List<DeliveryStatus> parsedStatuses = statuses == null ? null :
                statuses.stream().map(s -> DeliveryStatus.valueOf(s.toUpperCase())).toList();

        ListNotificationEventsQuery query = new ListNotificationEventsQuery(
                clientId,
                dateFrom != null ? Instant.parse(dateFrom) : null,
                dateTo != null ? Instant.parse(dateTo) : null,
                parsedStatuses,
                page,
                Math.min(pageSize, 100)
        );

        PagedResult<NotificationEvent> result = listUseCase.list(query);
        return ResponseEntity.ok(PagedResponse.from(result, NotificationEventResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationEventResponse> getEvent(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {

        String clientId = jwtExtractor.extract(authHeader);
        NotificationEvent event = getUseCase.getById(id, clientId);
        return ResponseEntity.ok(NotificationEventResponse.from(event));
    }

    @PostMapping("/{id}/replay")
    public ResponseEntity<NotificationEventResponse> replayEvent(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable String id) {

        String clientId = jwtExtractor.extract(authHeader);
        NotificationEvent event = replayUseCase.replay(id, clientId);
        return ResponseEntity.ok(NotificationEventResponse.from(event));
    }
}
