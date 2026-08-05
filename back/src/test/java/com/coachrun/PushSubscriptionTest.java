package com.coachrun;

import com.coachrun.repository.PushSubscriptionRepository;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.GdprService;
import com.coachrun.service.PushNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Abonnements aux notifications push : propriété, déconnexion, droit à l'oubli.
 *
 * <p>Un endpoint WebPush est l'adresse d'un appareil — une donnée personnelle, et un canal ouvert
 * vers son porteur. Trois règles en découlent, qu'aucun test ne tenait jusqu'ici : on ne
 * désabonne que ses propres appareils ; se déconnecter coupe le canal en même temps que les
 * jetons ; effacer un dossier au titre du droit à l'oubli emporte les abonnements, que la table
 * ne rattache par aucune clé étrangère.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushSubscriptionTest {

    private static final String KEYS = "\"keys\":{\"p256dh\":\"BNc-cle-publique\",\"auth\":\"secret\"}";

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PushSubscriptionRepository subscriptionRepository;
    @Autowired private PushNotificationService pushService;
    @Autowired private GdprService gdprService;

    private MockMvc mvc;
    private String coachBearer;
    private UUID coachUserId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + auth.get("accessToken").asText();
        coachUserId = UUID.fromString(auth.get("user").get("id").asText());
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void subscribe(String bearer, String endpoint) throws Exception {
        subscribe(bearer, endpoint, null);
    }

    private void subscribe(String bearer, String endpoint, String userAgent) throws Exception {
        var request = post("/push/subscribe").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endpoint\":\"" + endpoint + "\"," + KEYS + "}");
        if (userAgent != null) {
            request = request.header("User-Agent", userAgent);
        }
        mvc.perform(request).andExpect(status().isNoContent());
    }

    /**
     * Les appareils se listent avec un nom reconnaissable, et <b>sans jamais leur endpoint</b> :
     * c'est une URL secrète, qui donne à qui la détient le pouvoir de pousser sur l'appareil.
     */
    @Test
    void devicesAreListedByNameAndNeverExposeTheirEndpoint() throws Exception {
        subscribe(coachBearer, "https://fcm.example/coach-phone",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");

        // Lu en octets : MockMvc décode par défaut avec un jeu qui abîme les accents et le « · ».
        byte[] raw = mvc.perform(get("/push/devices").header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String body = new String(raw, java.nio.charset.StandardCharsets.UTF_8);

        JsonNode devices = objectMapper.readTree(raw);
        assertThat(devices).hasSize(1);
        assertThat(devices.get(0).get("label").asText()).isEqualTo("iPhone · Safari");
        assertThat(devices.get(0).get("fingerprint").asText()).hasSize(16);
        assertThat(body).doesNotContain("fcm.example");
    }

    /**
     * Retrait ciblé d'un appareil. Il n'existait qu'un retrait par endpoint, c'est-à-dire depuis
     * l'appareil lui-même : un téléphone perdu ou revendu restait abonné jusqu'à ce que son
     * navigateur réponde 410 — ce qui peut ne jamais arriver.
     */
    @Test
    void theOwnerRemovesOneDeviceAmongSeveral() throws Exception {
        subscribe(coachBearer, "https://fcm.example/coach-phone", "Mozilla/5.0 (iPhone)");
        subscribe(coachBearer, "https://fcm.example/coach-laptop", "Mozilla/5.0 (Macintosh) Chrome/120");

        JsonNode devices = objectMapper.readTree(
                mvc.perform(get("/push/devices").header("Authorization", coachBearer))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String phoneId = devices.get(0).get("label").asText().startsWith("iPhone")
                ? devices.get(0).get("id").asText() : devices.get(1).get("id").asText();

        mvc.perform(delete("/push/devices/{id}", phoneId).header("Authorization", coachBearer))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByUserId(coachUserId)).hasSize(1);
        assertThat(subscriptionRepository.findByEndpoint("https://fcm.example/coach-laptop")).isPresent();
    }

    /** L'appareil d'autrui n'est pas retirable, et le refus est muet : répondre 404 le révélerait. */
    @Test
    void anotherAccountCannotRemoveThisDeviceById() throws Exception {
        subscribe(coachBearer, "https://fcm.example/coach-phone", "Mozilla/5.0 (iPhone)");
        String deviceId = objectMapper.readTree(
                mvc.perform(get("/push/devices").header("Authorization", coachBearer))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();
        String athleteBearer = "Bearer " + login(DemoSeedService.ATHLETE_EMAIL).get("accessToken").asText();

        mvc.perform(delete("/push/devices/{id}", deviceId).header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByEndpoint("https://fcm.example/coach-phone")).isPresent();
    }

    @Test
    void aDeviceRegistersOnceEvenIfTheBrowserReSendsIt() throws Exception {
        subscribe(coachBearer, "https://fcm.example/coach-1");
        subscribe(coachBearer, "https://fcm.example/coach-1");

        assertThat(subscriptionRepository.findByUserId(coachUserId)).hasSize(1);
        assertThat(pushService.canReach(coachUserId)).isFalse(); // VAPID absent en test
    }

    /** L'endpoint d'autrui reste intouchable : la route ne prenait aucun contrôle de propriété. */
    @Test
    void anotherAccountCannotRevokeThisDevice() throws Exception {
        subscribe(coachBearer, "https://fcm.example/coach-1");
        String athleteBearer = "Bearer " + login(DemoSeedService.ATHLETE_EMAIL).get("accessToken").asText();

        mvc.perform(delete("/push/subscribe").param("endpoint", "https://fcm.example/coach-1")
                        .header("Authorization", athleteBearer))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByEndpoint("https://fcm.example/coach-1")).isPresent();
    }

    @Test
    void theOwnerRevokesItsOwnDevice() throws Exception {
        subscribe(coachBearer, "https://fcm.example/coach-1");

        mvc.perform(delete("/push/subscribe").param("endpoint", "https://fcm.example/coach-1")
                        .header("Authorization", coachBearer))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByEndpoint("https://fcm.example/coach-1")).isEmpty();
    }

    /**
     * Téléphone partagé : sans cette coupure côté serveur, l'appareil continue d'annoncer les
     * séances et les retours du compte précédent sur son écran verrouillé.
     */
    @Test
    void loggingOutSilencesEveryDeviceOfTheAccount() throws Exception {
        subscribe(coachBearer, "https://fcm.example/coach-1");
        subscribe(coachBearer, "https://fcm.example/coach-2");

        mvc.perform(post("/auth/logout").header("Authorization", coachBearer))
                .andExpect(status().isNoContent());

        assertThat(subscriptionRepository.findByUserId(coachUserId)).isEmpty();
    }

    /**
     * Droit à l'oubli : {@code push_subscriptions} porte un {@code user_id} nu, sans clé
     * étrangère — aucune cascade ne l'atteint, les lignes survivaient à la suppression du dossier.
     */
    @Test
    void erasingAnAthleteTakesItsSubscriptionsWithIt() throws Exception {
        JsonNode auth = login(DemoSeedService.ATHLETE_EMAIL);
        String athleteBearer = "Bearer " + auth.get("accessToken").asText();
        UUID athleteUserId = UUID.fromString(auth.get("user").get("id").asText());
        UUID athleteId = UUID.fromString(auth.get("user").get("athleteId").asText());
        subscribe(athleteBearer, "https://fcm.example/athlete-1");

        gdprService.deleteAthleteData(athleteId);

        assertThat(subscriptionRepository.findByUserId(athleteUserId)).isEmpty();
    }
}
