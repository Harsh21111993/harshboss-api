package com.harshboss.jev.client;

import com.harshboss.jev.dto.JevRequest;
import com.harshboss.jev.dto.JevResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Declarative HTTP client for the Jev AI API (TypeSafe AI).
 *
 * <p>Uses Spring Boot 4's declarative HTTP Interface with RestClient.
 * The bean is created in {@link com.harshboss.jev.config.JevConfiguration}.</p>
 *
 * <p>Jev endpoint: POST /v1/systemone</p>
 */
@HttpExchange(url = "/v1/systemone")
public interface JevClient {

    /**
     * Evaluate one or more typed questions against the given state.
     *
     * @param request contains the state (context) + questions (Choice/Score/Noul)
     * @return typed results with values, confidence, and probabilities
     */
    @PostExchange
    JevResponse evaluate(@RequestBody JevRequest request);
}
