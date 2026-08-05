package com.coachrun;

import com.coachrun.service.PushNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Charge utile push : elle doit rester du JSON valide quoi que le coach ait tapé dans le titre
 * d'une séance.
 *
 * <p>Ce test existe pour un défaut qui ne se voyait nulle part : l'échappement était fait à la
 * main et ne traitait que {@code \} et {@code "}. Un titre contenant un retour à la ligne
 * produisait un JSON invalide, le service worker n'affichait rien, et aucune erreur n'était levée
 * — ni côté serveur, ni côté navigateur. Un canal muet est indiscernable d'un canal sans message.</p>
 */
class PushPayloadTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PushNotificationService service = new PushNotificationService(null, mapper);

    private JsonNode payload(String title, String body, String url,
                             List<PushNotificationService.QuickAction> actions, String tag) {
        String json = ReflectionTestUtils.invokeMethod(service, "payload", title, body, url, actions, tag);
        assertThat(json).isNotNull();
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Charge utile non parsable : " + json, e);
        }
    }

    @Test
    void survivesQuotesNewlinesAndBackslashesInTitle() {
        String hostile = "Séance \"seuil\"\n10×400m \\ récup 1'30\ttest";

        JsonNode root = payload(hostile, "corps", "https://app.test/athlete/today", List.of(), null);

        // Le titre traverse intact : c'est le contrat, pas un détail d'implémentation.
        assertThat(root.path("notification").path("title").asText()).isEqualTo(hostile);
    }

    @Test
    void carriesTagAndRenotifyTogether() {
        JsonNode notification = payload("Titre", "corps", "https://app.test/app", List.of(),
                "ATHLETE_FEEDBACK").path("notification");

        assertThat(notification.path("tag").asText()).isEqualTo("ATHLETE_FEEDBACK");
        // Sans renotify, une notification qui en remplace une autre arrive en silence : le second
        // retour d'athlète ne ferait plus ni bruit ni vibration.
        assertThat(notification.path("renotify").asBoolean()).isTrue();
    }

    @Test
    void omitsTagWhenNoneGiven() {
        JsonNode notification = payload("Titre", "corps", "https://app.test/app", List.of(), null)
                .path("notification");

        assertThat(notification.has("tag")).isFalse();
        assertThat(notification.has("renotify")).isFalse();
    }

    /**
     * Structure attendue par le service worker Angular : {@code data} <b>dans</b> le bloc
     * {@code notification}, et une destination par bouton d'action.
     */
    @Test
    void keepsNgswStructureForQuickActions() {
        List<PushNotificationService.QuickAction> actions = List.of(
                new PushNotificationService.QuickAction("rpe-3", "Facile (3)",
                        "https://app.test/athlete/today?feedback=1&rpe=3"),
                new PushNotificationService.QuickAction("rpe-8", "Dur (8)",
                        "https://app.test/athlete/today?feedback=1&rpe=8"));

        JsonNode notification = payload("Ta séance est finie ?", "corps",
                "https://app.test/athlete/today?feedback=1", actions, "SESSION_DEBRIEF")
                .path("notification");

        assertThat(notification.path("actions")).hasSize(2);
        assertThat(notification.path("actions").get(0).path("action").asText()).isEqualTo("rpe-3");

        JsonNode onActionClick = notification.path("data").path("onActionClick");
        assertThat(onActionClick.path("default").path("operation").asText())
                .isEqualTo("navigateLastFocusedOrOpen");
        // L'URL de l'action porte le RPE : c'est elle qui rend le retour « en deux taps » possible.
        assertThat(onActionClick.path("rpe-8").path("url").asText()).endsWith("&rpe=8");
    }
}
