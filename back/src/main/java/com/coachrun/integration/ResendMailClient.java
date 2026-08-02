package com.coachrun.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.coachrun.config.HttpClients;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
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
                // Sans read timeout, une connexion qui pend bloque le thread appelant sans fin.
                .requestFactory(HttpClients.timeouts())
                .build();
    }

    /** Envoi HTML seul (sans version texte) — à éviter, cf. {@link #send(String, String, String, String, String, String)}. */
    public void send(String to, String subject, String html) {
        send(to, subject, html, null, null, null);
    }

    /**
     * Envoi complet d'un e-mail transactionnel.
     *
     * @param text            version texte (améliore la délivrabilité et sert les clients sans HTML)
     * @param replyTo         adresse de réponse, ou {@code null}
     * @param listUnsubscribe valeur de l'en-tête {@code List-Unsubscribe}, ou {@code null}
     */
    public void send(String to, String subject, String html, String text,
                     String replyTo, String listUnsubscribe) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));
        body.put("subject", subject);
        body.put("html", html);
        if (StringUtils.hasText(text)) {
            body.put("text", text);
        }
        if (StringUtils.hasText(replyTo)) {
            body.put("reply_to", replyTo);
        }
        if (StringUtils.hasText(listUnsubscribe)) {
            body.put("headers", Map.of(
                    "List-Unsubscribe", listUnsubscribe,
                    "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
        }
        restClient.post().uri("/emails").body(body).retrieve().toBodilessEntity();
        log.debug("Email envoyé à {} (sujet: {})", to, subject);
    }
}
