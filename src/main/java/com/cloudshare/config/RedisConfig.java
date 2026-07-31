package com.cloudshare.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@Configuration
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String cacheHost;

    @Value("${spring.data.redis.port:6379}")
    private int cachePort;

    @Value("${spring.data.redis.password:}")
    private String cachePassword;

    @Value("${security.redis.host:localhost}")
    private String securityHost;

    @Value("${security.redis.port:6380}")
    private int securityPort;

    @Value("${security.redis.password:}")
    private String securityPassword;

    @Value("${security.rate-limiting.redis.host:localhost}")
    private String rateLimitHost;

    @Value("${security.rate-limiting.redis.port:6381}")
    private int rateLimitPort;

    @Value("${security.rate-limiting.redis.password:}")
    private String rateLimitPassword;

    @Primary
    @Bean(name = "cacheConnectionFactory")
    public LettuceConnectionFactory cacheConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(cacheHost, cachePort);
        applyPassword("cache", config, cachePassword);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "securityConnectionFactory")
    public LettuceConnectionFactory securityConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(securityHost, securityPort);
        applyPassword("security", config, securityPassword);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "rateLimitConnectionFactory")
    public LettuceConnectionFactory rateLimitConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(rateLimitHost, rateLimitPort);
        applyPassword("rate-limit", config, rateLimitPassword);
        return new LettuceConnectionFactory(config);
    }

    /**
     * Applies a password to a Redis connection if one is configured. Logs a loud
     * warning (rather than silently proceeding) if a Redis instance is left
     * unauthenticated, since these instances hold token blacklists, MFA replay
     * guards, and refresh-token families.
     */
    private void applyPassword(String label, RedisStandaloneConfiguration config, String password) {
        if (StringUtils.hasText(password)) {
            config.setPassword(password);
        } else {
            log.warn("Redis instance '{}' is configured WITHOUT a password (requirepass). " +
                    "This is only acceptable when network isolation is guaranteed. " +
                    "Set the corresponding *_REDIS_PASSWORD environment variable.", label);
        }
    }

    @Primary
    @Bean(name = "redisTemplate")
    public StringRedisTemplate cacheRedisTemplate(
            @Qualifier("cacheConnectionFactory") LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = "securityRedisTemplate")
    public StringRedisTemplate securityRedisTemplate(
            @Qualifier("securityConnectionFactory") LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(name = "rateLimitRedisTemplate")
    public StringRedisTemplate rateLimitRedisTemplate(
            @Qualifier("rateLimitConnectionFactory") LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
