package com.shubham.gatekeeper.controller;

import com.shubham.gatekeeper.model.ApiRoute;
import com.shubham.gatekeeper.repository.ApiRouteRepository;
import com.shubham.gatekeeper.service.RouteEventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/routes")
public class AdminRouteController {
    private final ApiRouteRepository routes;
    private final RouteEventPublisher events;
    public AdminRouteController(ApiRouteRepository routes, RouteEventPublisher events) {
        this.routes = routes; this.events = events;
    }
    @GetMapping public List<ApiRoute> all() { return routes.findAll(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiRoute create(@Valid @RequestBody ApiRoute route) {
        if (routes.findByRouteKey(route.getRouteKey()).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Route key already exists");
        ApiRoute saved = routes.save(route); events.publish("ROUTE_CREATED", saved); return saved;
    }
    @PutMapping("/{id}") public ApiRoute update(@PathVariable Long id, @Valid @RequestBody ApiRoute input) {
        ApiRoute route = routes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        route.setTargetUrl(input.getTargetUrl()); route.setActive(input.isActive());
        route.setRequestsPerMinute(input.getRequestsPerMinute());
        ApiRoute saved = routes.save(route); events.publish("ROUTE_UPDATED", saved); return saved;
    }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        ApiRoute route = routes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        routes.delete(route); events.publish("ROUTE_DELETED", route);
    }
}