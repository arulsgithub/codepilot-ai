package com.codepilot.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AIClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient nemotronWebClient(
            WebClient.Builder builder,
            NemotronProperties properties) {

        return builder
                .baseUrl(properties.baseUrl())
                .defaultHeader(
                        "Authorization",
                        "Bearer " + properties.apiKey()
                )
                .defaultHeader(
                        "Content-Type",
                        "application/json"
                )
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}