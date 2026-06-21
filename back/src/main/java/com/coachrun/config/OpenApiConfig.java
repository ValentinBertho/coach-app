package com.coachrun.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Métadonnées de la documentation OpenAPI / Swagger UI (servie sous /api/swagger-ui.html).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI coachRunOpenAPI(@Value("${app.version:dev}") String version) {
        return new OpenAPI().info(new Info()
                .title("CoachRun API")
                .description("API de la plateforme de coaching course à pied")
                .version(version)
                .license(new License().name("Privée")));
    }
}
