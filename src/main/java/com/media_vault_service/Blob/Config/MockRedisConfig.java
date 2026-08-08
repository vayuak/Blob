package com.media_vault_service.Blob.Config;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

@Configuration
// 🟢 CRITICAL FIX: This allows the Mock to run locally, but stays completely out of "prod"
@Profile({"default", "test", "dev"})
public class MockRedisConfig {

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> mockValueOps = Mockito.mock(ValueOperations.class);

        Mockito.when(template.opsForValue()).thenReturn(mockValueOps);
        Mockito.when(template.execute(ArgumentMatchers.any(RedisCallback.class))).thenReturn(null);

        // 🟢 Simulates a fake Redis database returning a valid OTP for local testing
        Mockito.when(mockValueOps.get(ArgumentMatchers.anyString())).thenReturn("123456");

        return template;
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @Primary
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = Mockito.mock(RedisTemplate.class);
        ValueOperations<String, Object> mockValueOps = Mockito.mock(ValueOperations.class);

        // Map the operations
        Mockito.when(template.opsForValue()).thenReturn(mockValueOps);

        // 🟢 STOPS THE NPE: Explicitly intercept transactional/connection logic
        Mockito.when(template.execute(ArgumentMatchers.any(RedisCallback.class))).thenReturn(null);
        Mockito.when(template.delete(ArgumentMatchers.anyString())).thenReturn(true);
        Mockito.when(template.hasKey(ArgumentMatchers.anyString())).thenReturn(false);

        // Safely intercept set operations so they don't crash when saving users/OTPs
        Mockito.doNothing().when(mockValueOps).set(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(),
                ArgumentMatchers.any(Duration.class)
        );

        return template;
    }
}