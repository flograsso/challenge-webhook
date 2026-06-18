package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.domain.exception.EventAlreadyCompletedException;
import com.cobre.eventsApi.domain.exception.EventAlreadyInProgressException;
import com.cobre.eventsApi.domain.exception.EventNotFoundException;
import com.cobre.eventsApi.domain.model.*;
import com.cobre.eventsApi.domain.port.in.GetNotificationEventUseCase;
import com.cobre.eventsApi.domain.port.in.ListNotificationEventsUseCase;
import com.cobre.eventsApi.domain.port.in.ReplayNotificationEventUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class NotificationEventControllerTest {

    @Autowired
    WebApplicationContext webApplicationContext;

    MockMvc mockMvc;

    @MockitoBean
    ListNotificationEventsUseCase listUseCase;

    @MockitoBean
    GetNotificationEventUseCase getUseCase;

    @MockitoBean
    ReplayNotificationEventUseCase replayUseCase;

    @MockitoBean
    JwtClientIdExtractor jwtExtractor;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    private static final String AUTH_HEADER = "Bearer eyJhbGciOiJub25lIn0.eyJjbGllbnRfaWQiOiJDTElFTlQwMDEifQ.";
    private static final String CLIENT_ID = "CLIENT001";

    private NotificationEvent sampleEvent(String id, DeliveryStatus status) {
        return new NotificationEvent(id, "credit_card_payment", "Payment received",
                status, Instant.parse("2024-01-01T00:00:00Z"), null, CLIENT_ID, 0, null, null);
    }

    @Test
    void listEvents_returns200WithPagedResponse() throws Exception {
        when(jwtExtractor.extract(AUTH_HEADER)).thenReturn(CLIENT_ID);
        NotificationEvent event = sampleEvent("E1", DeliveryStatus.COMPLETED);
        PagedResult<NotificationEvent> result = new PagedResult<>(List.of(event), 1, 20, 1L, 1);
        when(listUseCase.list(any())).thenReturn(result);

        mockMvc.perform(get("/notification_events")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].eventId").value("E1"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void getEvent_returns200WithSingleEvent() throws Exception {
        when(jwtExtractor.extract(AUTH_HEADER)).thenReturn(CLIENT_ID);
        when(getUseCase.getById("E1", CLIENT_ID)).thenReturn(sampleEvent("E1", DeliveryStatus.COMPLETED));

        mockMvc.perform(get("/notification_events/E1")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("E1"))
                .andExpect(jsonPath("$.deliveryStatus").value("COMPLETED"));
    }

    @Test
    void replayEvent_returns200WithUpdatedEvent() throws Exception {
        when(jwtExtractor.extract(AUTH_HEADER)).thenReturn(CLIENT_ID);
        NotificationEvent replayed = sampleEvent("E1", DeliveryStatus.COMPLETED);
        when(replayUseCase.replay("E1", CLIENT_ID)).thenReturn(replayed);

        mockMvc.perform(post("/notification_events/E1/replay")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("E1"))
                .andExpect(jsonPath("$.deliveryStatus").value("COMPLETED"));
    }

    @Test
    void missingAuthHeader_returns401() throws Exception {
        mockMvc.perform(get("/notification_events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void getEvent_unknownId_returns404() throws Exception {
        when(jwtExtractor.extract(AUTH_HEADER)).thenReturn(CLIENT_ID);
        when(getUseCase.getById("MISSING", CLIENT_ID)).thenThrow(new EventNotFoundException("MISSING"));

        mockMvc.perform(get("/notification_events/MISSING")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void replayEvent_alreadyCompleted_returns409() throws Exception {
        when(jwtExtractor.extract(AUTH_HEADER)).thenReturn(CLIENT_ID);
        when(replayUseCase.replay("E1", CLIENT_ID)).thenThrow(new EventAlreadyCompletedException("E1"));

        mockMvc.perform(post("/notification_events/E1/replay")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_ALREADY_COMPLETED"));
    }

    @Test
    void replayEvent_alreadyInProgress_returns422() throws Exception {
        when(jwtExtractor.extract(AUTH_HEADER)).thenReturn(CLIENT_ID);
        when(replayUseCase.replay("E1", CLIENT_ID)).thenThrow(new EventAlreadyInProgressException("E1", "PENDING"));

        mockMvc.perform(post("/notification_events/E1/replay")
                        .header("Authorization", AUTH_HEADER))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENT_ALREADY_IN_PROGRESS"));
    }
}
