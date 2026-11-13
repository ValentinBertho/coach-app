package com.coachrun;

import com.coachrun.service.GarminService;
import com.coachrun.service.GarminWebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Notifications push de Garmin. L'adresse est publique et Garmin ne signe rien : le jeton secret
 * dans l'URL est la seule barrière, et ces tests la fixent.
 */
@ExtendWith(MockitoExtension.class)
class GarminWebhookTest {

    @Mock private GarminService garminService;
    @Mock private com.coachrun.service.GarminActivityService activityService;

    @Test
    void sansJetonConfigureToutEstRefuse() {
        GarminWebhookService service = new GarminWebhookService(garminService, activityService, "");
        assertThat(service.verifies("")).isFalse();
        assertThat(service.verifies(null)).isFalse();
        assertThat(service.verifies("n-importe-quoi")).isFalse();
    }

    @Test
    void seulLeBonJetonEstAccepte() {
        GarminWebhookService service = new GarminWebhookService(garminService, activityService, "secret-convenu");
        assertThat(service.verifies("secret-convenu")).isTrue();
        assertThat(service.verifies("secret-convenu-bis")).isFalse();
        assertThat(service.verifies(null)).isFalse();
    }

    @Test
    void uneDesinscriptionEstTransmise() {
        GarminWebhookService service = new GarminWebhookService(garminService, activityService, "s");
        service.accept(Map.of("deregistrations", List.of(Map.of("userId", "u-1"))));
        verify(garminService, timeout(2000)).handleDeregistration("u-1");
    }

    @Test
    void unChangementDePermissionsEstTransmis() {
        GarminWebhookService service = new GarminWebhookService(garminService, activityService, "s");
        service.accept(Map.of("userPermissionsChange", List.of(Map.of(
                "userId", "u-2", "permissions", List.of("ACTIVITY_EXPORT")))));
        verify(garminService, timeout(2000)).handlePermissionsChange("u-2", List.of("ACTIVITY_EXPORT"));
    }

    @Test
    void unContenuInattenduNeDeclencheRien() {
        GarminWebhookService service = new GarminWebhookService(garminService, activityService, "s");
        service.accept(Map.of("deregistrations", "pas-une-liste", "autre", List.of(1, 2)));
        service.accept(null);
        verify(garminService, after(300).never()).handleDeregistration(anyString());
        verify(garminService, never()).handlePermissionsChange(anyString(), any());
    }

    @Test
    void lesSortiesSontTransmisesParUtilisateurAvecLeurForme() {
        GarminWebhookService service = new GarminWebhookService(garminService, activityService, "s");
        service.accept(Map.of(
                "activities", List.of(Map.of("userId", "u-3", "activityId", 1L),
                        Map.of("userId", "u-3", "activityId", 2L)),
                "activityDetails", List.of(Map.of("userId", "u-4", "activityId", 3L))));
        verify(activityService, timeout(2000)).handle(org.mockito.ArgumentMatchers.eq("u-3"),
                argThat(l -> l.size() == 2), org.mockito.ArgumentMatchers.eq(false));
        verify(activityService, timeout(2000)).handle(org.mockito.ArgumentMatchers.eq("u-4"),
                argThat(l -> l.size() == 1), org.mockito.ArgumentMatchers.eq(true));
    }

    // --- Adresse déclarée chez Garmin : le jeton dans le chemin ---

    @Test
    void leJetonDansLeCheminEstAccepte() throws Exception {
        GarminWebhookService service = org.mockito.Mockito.mock(GarminWebhookService.class);
        org.mockito.Mockito.when(service.verifies("secret-convenu")).thenReturn(true);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.coachrun.controller.GarminWebhookController(service)).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/public/garmin/webhook/secret-convenu")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"deregistrations\":[{\"userId\":\"u-9\"}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        verify(service).accept(org.mockito.ArgumentMatchers.argThat(b -> b != null && b.containsKey("deregistrations")));
    }

    @Test
    void unMauvaisJetonDansLeCheminEstIgnoreMaisAcquitte() throws Exception {
        GarminWebhookService service = org.mockito.Mockito.mock(GarminWebhookService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.coachrun.controller.GarminWebhookController(service)).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/public/garmin/webhook/mauvais")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"deregistrations\":[{\"userId\":\"u-9\"}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        verify(service, never()).accept(any());
    }
}
