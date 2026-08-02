package com.coachrun;

import com.coachrun.config.StartupSecretsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garde-fou de démarrage en production. Chaque réglage couvert ici produit, s'il est absent, une
 * panne <b>silencieuse</b> : liens d'invitation pointant sur localhost, e-mails qui échouent dans
 * les logs, origine de développement autorisée à parler à la prod, push inerte faute de clés VAPID
 * alors que « séance planifiée » et « commentaire du coach » n'ont plus de repli e-mail.
 */
class StartupSecretsValidatorTest {

    private static final String GOOD_JWT = "x".repeat(64);
    private static final String GOOD_KEY = "a1b2c3d4".repeat(8); // 64 hex
    private static final String GOOD_URL = "https://www.darilab.app";
    private static final String GOOD_CORS = "https://www.darilab.app,https://darilab.app";
    private static final String GOOD_VAPID_PUB = "BJ-public-key";
    private static final String GOOD_VAPID_PRIV = "private-key";

    private StartupSecretsValidator validator(String jwt, String key, String url, boolean mailEnabled,
                                              String resendKey, String cors, String vapidPub, String vapidPriv) {
        return new StartupSecretsValidator(jwt, key, url, mailEnabled, resendKey, cors, vapidPub, vapidPriv);
    }

    private void run(StartupSecretsValidator validator) {
        ReflectionTestUtils.invokeMethod(validator, "validate");
    }

    @Test
    void completeConfigurationBoots() {
        assertThatCode(() -> run(validator(GOOD_JWT, GOOD_KEY, GOOD_URL, true, "re_key",
                GOOD_CORS, GOOD_VAPID_PUB, GOOD_VAPID_PRIV))).doesNotThrowAnyException();
    }

    @Test
    void frontendUrlLeftOnLocalhostFailsBoot() {
        assertThatThrownBy(() -> run(validator(GOOD_JWT, GOOD_KEY, "http://localhost:4200", false, "",
                GOOD_CORS, GOOD_VAPID_PUB, GOOD_VAPID_PRIV)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRONTEND_URL");
    }

    @Test
    void mailEnabledWithoutApiKeyFailsBoot() {
        assertThatThrownBy(() -> run(validator(GOOD_JWT, GOOD_KEY, GOOD_URL, true, "",
                GOOD_CORS, GOOD_VAPID_PUB, GOOD_VAPID_PRIV)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESEND_API_KEY");
    }

    @Test
    void corsAllowingLocalhostFailsBoot() {
        assertThatThrownBy(() -> run(validator(GOOD_JWT, GOOD_KEY, GOOD_URL, false, "",
                "https://www.darilab.app,http://localhost:4200", GOOD_VAPID_PUB, GOOD_VAPID_PRIV)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CORS_ORIGINS");
    }

    @Test
    void missingVapidKeysFailBoot() {
        assertThatThrownBy(() -> run(validator(GOOD_JWT, GOOD_KEY, GOOD_URL, false, "",
                GOOD_CORS, "", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VAPID");
    }

    /** Les manques sont cumulés dans un seul message : on corrige tout en un seul redéploiement. */
    @Test
    void allProblemsAreReportedAtOnce() {
        assertThatThrownBy(() -> run(validator("dev-secret", "zzz", "http://localhost:4200", true, "",
                "http://localhost:4200", "", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("FIELD_ENCRYPTION_KEY")
                .hasMessageContaining("FRONTEND_URL")
                .hasMessageContaining("RESEND_API_KEY")
                .hasMessageContaining("CORS_ORIGINS")
                .hasMessageContaining("VAPID");
    }
}
