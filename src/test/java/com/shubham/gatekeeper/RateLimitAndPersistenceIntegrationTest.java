package com.shubham.gatekeeper;

import com.shubham.gatekeeper.model.ApiRoute;
import com.shubham.gatekeeper.repository.ApiRouteRepository;
import com.shubham.gatekeeper.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RateLimitAndPersistenceIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
        .withDatabaseName("gatekeeper").withUsername("gatekeeper").withPassword("gatekeeper");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @Autowired private RateLimitService rateLimitService;
    @Autowired private ApiRouteRepository routeRepository;

    @Test
    void tokenBucketEnforcesCapacityAgainstRealRedis() {
        String key = "integration:route:client:" + System.nanoTime();
        assertThat(rateLimitService.allow(key, 2)).isTrue();
        assertThat(rateLimitService.allow(key, 2)).isTrue();
        assertThat(rateLimitService.allow(key, 2)).isFalse();
    }

    @Test
    void routeCanBePersistedAndReadBackFromRealMysql() {
        String routeKey = "it-" + Long.toString(System.nanoTime(), 36);
        ApiRoute saved = routeRepository.saveAndFlush(
            new ApiRoute(routeKey, "http://localhost:9099", 30));
        ApiRoute loaded = routeRepository.findByRouteKey(routeKey).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getTargetUrl()).isEqualTo("http://localhost:9099");
        assertThat(loaded.getRequestsPerMinute()).isEqualTo(30);
    }
}
