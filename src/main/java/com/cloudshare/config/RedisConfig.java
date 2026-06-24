package com.cloudshare.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String cacheHost;

    @Value("${spring.data.redis.port:6379}")
    private int cachePort;

    @Value("${security.redis.host:localhost}")
    private String securityHost;

    @Value("${security.redis.port:6380}")
    private int securityPort;

    @Primary
    @Bean(name = "cacheConnectionFactory")
    public LettuceConnectionFactory cacheConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(cacheHost, cachePort);
        return new LettuceConnectionFactory(config);
    }

    @Bean(name = "securityConnectionFactory")
    public LettuceConnectionFactory securityConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(securityHost, securityPort);
        return new LettuceConnectionFactory(config);
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
}
