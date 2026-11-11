package com.shubham.gatekeeper.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimitService {
    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        TOKEN_BUCKET_SCRIPT.setScriptText("""
            local capacity = math.max(1, tonumber(ARGV[1]))
            local now_parts = redis.call('TIME')
            local now_ms = (tonumber(now_parts[1]) * 1000) + math.floor(tonumber(now_parts[2]) / 1000)
            local refill_per_ms = capacity / 60000
            local values = redis.call('HMGET', KEYS[1], 'tokens', 'updated_at')
            local tokens = tonumber(values[1])
            local updated_at = tonumber(values[2])

            if tokens == nil or updated_at == nil then
                tokens = capacity
                updated_at = now_ms
            else
                local elapsed_ms = math.max(0, now_ms - updated_at)
                tokens = math.min(capacity, tokens + (elapsed_ms * refill_per_ms))
            end

            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end

            redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated_at', now_ms)
            redis.call('PEXPIRE', KEYS[1], 120000)
            return allowed
            """);
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Atomically consumes a token from a bucket that refills over 60 seconds. */
    public boolean allow(String key, int requestsPerMinute) {
        String redisKey = "gatekeeper:token-bucket:" + key;
        Long allowed = redis.execute(TOKEN_BUCKET_SCRIPT, List.of(redisKey),
            Integer.toString(Math.max(1, requestsPerMinute)));
        return Long.valueOf(1L).equals(allowed);
    }
}