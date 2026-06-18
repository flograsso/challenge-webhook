package com.cobre.eventsApi.adapter.in.rest;

import com.cobre.eventsApi.domain.exception.InvalidTokenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class JwtClientIdExtractor {

    private final ObjectMapper objectMapper;

    public JwtClientIdExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String extract(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or invalid Authorization header");
        }

        String token = authorizationHeader.substring(7);
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new InvalidTokenException("Malformed JWT token");
        }

        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(payloadBytes);
            JsonNode clientIdNode = payload.path("client_id");
            if (clientIdNode.isMissingNode() || clientIdNode.isNull()) {
                throw new InvalidTokenException("JWT payload missing 'client_id' claim");
            }
            return clientIdNode.asText();
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("Failed to parse JWT payload: " + e.getMessage());
        }
    }
}
