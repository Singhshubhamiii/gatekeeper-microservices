package com.shubham.gatekeeper.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class RateLimitService {
    private final StringRedisTemplate redis;
    public RateLimitService(StringRedisTemplate redis) { this.redis = redis; }

    public boolean allow(String key, int limit) {
        String bucket = "gatekeeper:rate:" + key + ":" + java.time.LocalDateTime.now().withSecond(0).withNano(0);
        Long count = redis.opsForValue().increment(bucket);
        if (count != null && count == 1L) redis.expire(bucket, Duration.ofMinutes(2));
        return count != null && count <= limit;
    }
}