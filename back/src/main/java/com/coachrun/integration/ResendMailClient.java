package com.coachrun.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Client e-mail Resend (HTTP). Encapsule l'appel API ; n'est sollicité que lorsque
 * l'envoi est activé (cf. {@code app.mail.enabled}). Aucune donnée de santé dans les emails.
 */
@Slf4j
@Component
public class ResendMailClient {

    private final RestClient restClient;
    private final String from;

    public ResendMailClient(
            @Value("${app.mail.resend-api-key:}") String apiKey,
            @Value("${app.mail.from}") String from) {
        this.from = from;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public void send(String to, String subject, String html) {
        Map<String, Object> body = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "html", html);
        restClient.post().uri("/emails").body(body).retrieve().toBodilessEntity();
        log.debug("Email envoyé à {} (sujet: {})", to, subject);
    }
}
