package com.shubham.gatekeeper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shubham.gatekeeper.model.ApiRoute;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class RouteEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    public RouteEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper mapper) {
        this.kafka = kafka; this.mapper = mapper;
    }
    public void publish(String event, ApiRoute route) {
        try { kafka.send("gateway.route-events", route.getRouteKey(), mapper.writeValueAsString(
            java.util.Map.of("event", event, "routeKey", route.getRouteKey(), "targetUrl", route.getTargetUrl(), "active", route.isActive()))); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Could not serialize route event", ex); }
    }
}