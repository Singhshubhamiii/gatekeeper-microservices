package com.shubham.gatekeeper.repository;

import com.shubham.gatekeeper.model.ApiRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiRouteRepository extends JpaRepository<ApiRoute, Long> {
    Optional<ApiRoute> findByRouteKey(String routeKey);
}