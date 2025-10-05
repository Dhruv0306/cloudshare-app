package com.cloud.computing.filesharingapp.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EnvironmentConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentConfig.class);

    @Override
    public void initialize(@org.springframework.lang.NonNull ConfigurableApplicationContext applicationContext) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();

            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            Map<String, Object> envMap = new HashMap<>();

            dotenv.entries().forEach(entry -> {
                envMap.put(entry.getKey(), entry.getValue());
            });

            environment.getPropertySources().addFirst(new MapPropertySource("dotenv", envMap));
            logger.info("Loaded {} environment variables from .env file", envMap.size());

        } catch (Exception e) {
            logger.warn("Could not load .env file: {}", e.getMessage());
        }
    }
}