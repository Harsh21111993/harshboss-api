package com.harshboss.jev.config;

import com.harshboss.jev.client.JevClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Configuration for the Jev AI client (TypeSafe AI — System One decision model).
 *
 * <p>Creates a declarative HTTP Interface client that calls Jev's REST API
 * at {@code https://api.typesafe.ai/v1/systemone}.</p>
 *
 * <p>Jev is used as an ultra-fast (~100ms) decision gate that sits BEFORE
 * Gemini (the LLM) for classification, safety checks, and intent routing.
 * This dramatically reduces latency and cost for decisions that don't need
 * text generation.</p>
 *
 * <p>Set {@code harshboss.jev.enabled=false} to disable Jev and fall back
 * to LLM-only mode (all decisions go through Gemini).</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "harshboss.jev", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JevConfiguration {

    @Bean
    public JevClient jevClient(
            @Value("${harshboss.jev.base-url:https://api.typesafe.ai}") String baseUrl,
            @Value("${harshboss.jev.api-key:}") String apiKey) {

        log.info("Configuring Jev AI client: baseUrl={}, apiKey={}", baseUrl,
                apiKey != null && !apiKey.isBlank() ? "***configured***" : "NOT SET");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        RestClient restClient = builder.build();
        var adapter = RestClientAdapter.create(restClient);
        var factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(JevClient.class);
    }
}
