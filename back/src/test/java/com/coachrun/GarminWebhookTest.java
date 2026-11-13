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

    @Test
    void sansJetonConfigureToutEstRefuse() {
        GarminWebhookService service = new GarminWebhookService(garminService, "");
        assertThat(service.verifies("")).isFalse();
        assertThat(service.verifies(null)).isFalse();
        assertThat(service.verifies("n-importe-quoi")).isFalse();
    }

    @Test
    void seulLeBonJetonEstAccepte() {
        GarminWebhookService service = new GarminWebhookService(garminService, "secret-convenu");
        assertThat(service.verifies("secret-convenu")).isTrue();
        assertThat(service.verifies("secret-convenu-bis")).isFalse();
        assertThat(service.verifies(null)).isFalse();
    }

    @Test
    void uneDesinscriptionEstTransmise() {
        GarminWebhookService service = new GarminWebhookService(garminService, "s");
        service.accept(Map.of("deregistrations", List.of(Map.of("userId", "u-1"))));
        verify(garminService, timeout(2000)).handleDeregistration("u-1");
    }

    @Test
    void unChangementDePermissionsEstTransmis() {
        GarminWebhookService service = new GarminWebhookService(garminService, "s");
        service.accept(Map.of("userPermissionsChange", List.of(Map.of(
                "userId", "u-2", "permissions", List.of("ACTIVITY_EXPORT")))));
        verify(garminService, timeout(2000)).handlePermissionsChange("u-2", List.of("ACTIVITY_EXPORT"));
    }

    @Test
    void unContenuInattenduNeDeclencheRien() {
        GarminWebhookService service = new GarminWebhookService(garminService, "s");
        service.accept(Map.of("deregistrations", "pas-une-liste", "autre", List.of(1, 2)));
        service.accept(null);
        verify(garminService, after(300).never()).handleDeregistration(anyString());
        verify(garminService, never()).handlePermissionsChange(anyString(), any());
    }
}
