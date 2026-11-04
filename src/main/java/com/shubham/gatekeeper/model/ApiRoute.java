package com.shubham.gatekeeper.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;

@Entity
@Table(name = "api_routes", uniqueConstraints = @UniqueConstraint(columnNames = "routeKey"))
public class ApiRoute {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{2,60}") @Column(nullable = false, unique = true)
    private String routeKey;
    @NotBlank @Column(nullable = false, length = 1000)
    private String targetUrl;
    private boolean active = true;
    @Min(1) private int requestsPerMinute = 60;

    public ApiRoute() {}
    public ApiRoute(String routeKey, String targetUrl, int requestsPerMinute) {
        this.routeKey = routeKey; this.targetUrl = targetUrl; this.requestsPerMinute = requestsPerMinute; this.active = true;
    }
    public Long getId(){return id;} public String getRouteKey(){return routeKey;}
    public void setRouteKey(String v){routeKey=v;} public String getTargetUrl(){return targetUrl;}
    public void setTargetUrl(String v){targetUrl=v;} public boolean isActive(){return active;}
    public void setActive(boolean v){active=v;} public int getRequestsPerMinute(){return requestsPerMinute;}
    public void setRequestsPerMinute(int v){requestsPerMinute=v;}
}