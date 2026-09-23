package com.harshboss.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Provides a shared {@link WebClient.Builder} for outbound HTTP calls to
 * external platform APIs (Microsoft Graph, Google Calendar, Zoom).
 *
 * <p>The mock integration clients in {@code com.harshboss.integration} don't use
 * WebClient yet, but the bean is here so wiring the real APIs is a drop-in.</p>
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder.build();
    }
}
