package com.shubham.gatekeeper.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RateLimitService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        service = new RateLimitService(redis);
    }

    @Test
    void allowsRequestWithinConfiguredLimit() {
        when(values.increment(anyString())).thenReturn(1L);
        assertTrue(service.allow("users:127.0.0.1", 10));
        verify(redis).expire(anyString(), eq(Duration.ofMinutes(2)));
    }

    @Test
    void rejectsRequestAboveConfiguredLimit() {
        when(values.increment(anyString())).thenReturn(11L);
        assertFalse(service.allow("users:127.0.0.1", 10));
    }

    @Test
    void rejectsRequestWhenRedisCounterIsUnavailable() {
        when(values.increment(anyString())).thenReturn(null);
        assertFalse(service.allow("users:127.0.0.1", 10));
    }
}
