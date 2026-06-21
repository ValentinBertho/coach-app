package com.coachrun.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origines CORS autorisées, configurables via {@code app.cors.origins} (CSV / liste).
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> origins) {

    public CorsProperties {
        if (origins == null) {
            origins = List.of();
        }
    }
}
