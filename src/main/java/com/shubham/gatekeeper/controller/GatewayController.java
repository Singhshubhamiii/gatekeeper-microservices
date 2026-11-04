package com.shubham.gatekeeper.controller;

import com.shubham.gatekeeper.model.ApiRoute;
import com.shubham.gatekeeper.repository.ApiRouteRepository;
import com.shubham.gatekeeper.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.util.Enumeration;

@RestController
public class GatewayController {
    private final ApiRouteRepository routes;
    private final RateLimitService limiter;
    private final RestClient client;
    private final String gatewayKey;

    public GatewayController(ApiRouteRepository routes, RateLimitService limiter, RestClient.Builder builder,
            @Value("${GATEWAY_SHARED_KEY:local-demo-key}") String gatewayKey) {
        this.routes = routes; this.limiter = limiter; this.client = builder.build(); this.gatewayKey = gatewayKey;
    }

    @RequestMapping("/gateway/{routeKey}/**")
    public ResponseEntity<byte[]> forward(@PathVariable String routeKey, HttpServletRequest request,
                                          @RequestHeader(value="X-Gateway-Key", required=false) String suppliedKey,
                                          @RequestBody(required=false) byte[] body) {
        if (!java.security.MessageDigest.isEqual(gatewayKey.getBytes(), (suppliedKey == null ? "" : suppliedKey).getBytes()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid gateway key");
        ApiRoute route = routes.findByRouteKey(routeKey)
            .filter(ApiRoute::isActive).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found"));
        String clientId = request.getRemoteAddr();
        if (!limiter.allow(routeKey + ":" + clientId, route.getRequestsPerMinute()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        String prefix = "/gateway/" + routeKey;
        String path = request.getRequestURI().substring(prefix.length());
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        URI target = URI.create(route.getTargetUrl().replaceAll("/+$", "") + path + query);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        return client.method(method).uri(target).headers(headers -> {
            Enumeration<String> names = request.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (!name.equalsIgnoreCase("host") && !name.equalsIgnoreCase("content-length") &&
                    !name.equalsIgnoreCase("x-gateway-key")) headers.set(name, request.getHeader(name));
            }
        }).body(body == null ? new byte[0] : body).retrieve().toEntity(byte[].class);
    }
}