package com.shubham.gatekeeper.controller;

import com.shubham.gatekeeper.model.ApiRoute;
import com.shubham.gatekeeper.repository.ApiRouteRepository;
import com.shubham.gatekeeper.service.RateLimitEventPublisher;
import com.shubham.gatekeeper.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@RestController
public class GatewayController {
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final ApiRouteRepository routes;
    private final RateLimitService limiter;
    private final RateLimitEventPublisher rateLimitEvents;
    private final RestClient client;
    private final String gatewayKey;

    public GatewayController(ApiRouteRepository routes, RateLimitService limiter,
            RateLimitEventPublisher rateLimitEvents, RestClient.Builder builder,
            @Value("${GATEWAY_SHARED_KEY:local-demo-key}") String gatewayKey) {
        this.routes = routes;
        this.limiter = limiter;
        this.rateLimitEvents = rateLimitEvents;
        this.client = builder.build();
        this.gatewayKey = gatewayKey;
    }

    @RequestMapping("/gateway/{routeKey}/**")
    public ResponseEntity<byte[]> forward(@PathVariable String routeKey, HttpServletRequest request,
            @RequestHeader(value = "X-Gateway-Key", required = false) String suppliedKey,
            @RequestBody(required = false) byte[] body) {
        String provided = suppliedKey == null ? "" : suppliedKey;
        if (!MessageDigest.isEqual(gatewayKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid gateway key");
        }

        ApiRoute route = routes.findByRouteKey(routeKey).filter(ApiRoute::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));

        String clientId = request.getRemoteAddr();
        if (!limiter.allow(routeKey + ":" + clientId, route.getRequestsPerMinute())) {
            rateLimitEvents.publish(routeKey, clientId, route.getRequestsPerMinute());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }

        String prefix = "/gateway/" + routeKey;
        String path = request.getRequestURI().substring(prefix.length());
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        URI target = URI.create(route.getTargetUrl().replaceAll("/+$", "") + path + query);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        return client.method(method).uri(target).headers(headers -> copyRequestHeaders(request, headers))
            .body(body == null ? new byte[0] : body)
            .exchange((clientRequest, upstream) -> {
                HttpHeaders responseHeaders = new HttpHeaders();
                Set<String> excluded = new HashSet<>(HOP_BY_HOP_HEADERS);
                addConnectionTokens(upstream.getHeaders().getFirst(HttpHeaders.CONNECTION), excluded);
                upstream.getHeaders().forEach((name, values) -> {
                    if (!excluded.contains(name.toLowerCase(Locale.ROOT))) {
                        responseHeaders.put(name, values);
                    }
                });
                return ResponseEntity.status(upstream.getStatusCode())
                    .headers(responseHeaders)
                    .body(upstream.getBody().readAllBytes());
            });
    }

    private void copyRequestHeaders(HttpServletRequest request, HttpHeaders target) {
        Set<String> excluded = new HashSet<>(HOP_BY_HOP_HEADERS);
        excluded.add("x-gateway-key");
        addConnectionTokens(request.getHeader(HttpHeaders.CONNECTION), excluded);
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (!excluded.contains(name.toLowerCase(Locale.ROOT))) {
                target.put(name, Collections.list(request.getHeaders(name)));
            }
        }
    }

    private void addConnectionTokens(String connectionHeader, Set<String> excluded) {
        if (connectionHeader != null) {
            for (String token : connectionHeader.split(",")) {
                excluded.add(token.trim().toLowerCase(Locale.ROOT));
            }
        }
    }
}