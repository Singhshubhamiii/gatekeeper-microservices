package com.shubham.gatekeeper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class RateLimitEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RateLimitEventPublisher.class);
    private static final String TOPIC = "gateway.rate-limit-events";

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public RateLimitEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka;
        this.mapper = mapper;
    }

    public void publish(String routeKey, String clientId, int configuredLimit) {
        try {
            String payload = mapper.writeValueAsString(Map.of(
                "eventType", "RATE_LIMIT_EXCEEDED",
                "routeKey", routeKey,
                "clientId", clientId,
                "configuredRequestsPerMinute", configuredLimit,
                "occurredAt", Instant.now().toString()
            ));
            kafka.send(TOPIC, routeKey, payload).whenComplete((result, error) -> {
                if (error != null) {
                    log.warn("Could not publish rate-limit event for route {}", routeKey, error);
                }
            });
        } catch (JsonProcessingException | RuntimeException ex) {
            // Event publication is best-effort: a Kafka outage must not change the 429 response.
            log.warn("Could not publish rate-limit event for route {}", routeKey, ex);
        }
    }
}
