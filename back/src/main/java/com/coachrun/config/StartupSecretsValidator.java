package com.coachrun.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Garde-fou de démarrage en profil {@code prod} : l'application refuse de booter si les
 * secrets critiques sont manquants ou laissés à leurs valeurs de développement (cf. Techno.md §5).
 */
@Slf4j
@Component
@Profile("prod")
public class StartupSecretsValidator {

    private static final int MIN_JWT_SECRET_BYTES = 64; // HS512 → 512 bits
    private static final String DEV_JWT_SECRET_PREFIX = "dev-";
    private static final String DEV_ENCRYPTION_KEY = "0".repeat(64);

    private final String jwtSecret;
    private final String fieldEncryptionKey;

    public StartupSecretsValidator(
            @Value("${app.security.jwt.secret:}") String jwtSecret,
            @Value("${app.security.field-encryption-key:}") String fieldEncryptionKey) {
        this.jwtSecret = jwtSecret;
        this.fieldEncryptionKey = fieldEncryptionKey;
    }

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()
                || jwtSecret.startsWith(DEV_JWT_SECRET_PREFIX)
                || jwtSecret.getBytes().length < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET manquant ou trop faible en prod (≥ 512 bits requis, valeur de dev interdite).");
        }
        if (fieldEncryptionKey == null || !fieldEncryptionKey.matches("[0-9a-fA-F]{64}")
                || fieldEncryptionKey.equals(DEV_ENCRYPTION_KEY)) {
            throw new IllegalStateException(
                    "FIELD_ENCRYPTION_KEY manquant ou invalide en prod (64 hex requis, valeur de dev interdite).");
        }
        log.info("Validation des secrets de production OK.");
    }
}
