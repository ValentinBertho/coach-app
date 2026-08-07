package com.coachrun;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.service.StravaService;
import com.coachrun.service.StravaWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Réception des événements Strava : ce qui déclenche une synchronisation, et surtout ce qui n'en
 * déclenche pas.
 *
 * <p>L'URL de rappel est publique par obligation — Strava l'appelle sans jeton. Le tri fait ici
 * est donc la seule barrière, et ces règles la fixent.</p>
 */
@ExtendWith(MockitoExtension.class)
class StravaWebhookTest {

    private static final long OWNER = 4242L;
    private static final String OWNER_TEXT = "4242";

    @Mock private DeviceConnectionRepository connectionRepository;
    @Mock private StravaService stravaService;
    @InjectMocks private StravaWebhookService service;

    private final UUID athleteId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "verifyToken", "jeton-convenu");
    }

    /** Un athlète connu de cette instance est reconnu par son identifiant Strava. */
    private void athleteIsConnected() {
        when(connectionRepository.findAthleteIdsByProviderAndProviderAthleteId(
                DeviceProvider.STRAVA, OWNER_TEXT)).thenReturn(List.of(athleteId));
    }

    @Test
    void uneActiviteEnregistreeDeclencheLaSynchro() {
        athleteIsConnected();

        service.accept("activity", "create", OWNER, Map.of());

        verify(stravaService, timeout(2_000)).importForAthlete(athleteId);
    }

    /**
     * Une même sortie génère plusieurs événements (création, puis renommage, puis changement de
     * type). Les réimporter une fois par événement gaspillerait le quota Strava — 100 requêtes par
     * quart d'heure pour toute l'application.
     */
    @Test
    void uneRafaleDEvenementsNeDeclencheQuUneSynchro() {
        athleteIsConnected();
        // La synchro tient le thread le temps qu'on empile les événements suivants.
        when(stravaService.importForAthlete(athleteId)).thenAnswer(invocation -> {
            Thread.sleep(300);
            return 1;
        });

        service.accept("activity", "create", OWNER, Map.of());
        service.accept("activity", "update", OWNER, Map.of("title", "Sortie longue"));
        service.accept("activity", "update", OWNER, Map.of("type", "Run"));

        verify(stravaService, timeout(2_000)).importForAthlete(athleteId);
        verify(stravaService, after(300).times(1)).importForAthlete(athleteId);
    }

    /**
     * Une application Strava est unique : les événements de TOUS ses athlètes arrivent ici, y
     * compris ceux d'un environnement voisin. Un identifiant inconnu ne doit rien déclencher.
     */
    @Test
    void unAthleteInconnuNeDeclencheRien() {
        when(connectionRepository.findAthleteIdsByProviderAndProviderAthleteId(
                DeviceProvider.STRAVA, OWNER_TEXT)).thenReturn(List.of());

        service.accept("activity", "create", OWNER, Map.of());

        verify(stravaService, after(200).never()).importForAthlete(any());
    }

    /**
     * Une suppression côté Strava ne retire rien chez nous : la sortie a pu être notée, un ressenti
     * déposé, une séance rapprochée. On ne fait pas non plus disparaître ce travail par ricochet
     * d'une resynchronisation.
     */
    @Test
    void uneSuppressionCoteStravaNeToucheARien() {
        service.accept("activity", "delete", OWNER, Map.of());

        verify(stravaService, after(200).never()).importForAthlete(any());
        verify(stravaService, never()).disconnectForAthlete(any());
    }

    /** L'athlète a retiré l'accès depuis Strava : la connexion locale n'a plus de raison d'être. */
    @Test
    void uneRevocationDAccesRetireLaConnexion() {
        athleteIsConnected();

        service.accept("athlete", "update", OWNER, Map.of("authorized", "false"));

        verify(stravaService, timeout(2_000)).disconnectForAthlete(athleteId);
        verify(stravaService, never()).importForAthlete(any());
    }

    /** Une mise à jour de profil qui n'est pas une révocation ne doit surtout pas déconnecter. */
    @Test
    void uneMiseAJourDAthleteSansRevocationNeDeconnectePas() {
        service.accept("athlete", "update", OWNER, Map.of("authorized", "true"));

        verify(stravaService, after(200).never()).disconnectForAthlete(any());
    }

    /** Un échec d'import ne doit pas condamner l'athlète : le suivant doit repasser. */
    @Test
    void unEchecNeBloquePasLesEvenementsSuivants() {
        athleteIsConnected();
        when(stravaService.importForAthlete(athleteId)).thenThrow(new IllegalStateException("Strava HS"));

        service.accept("activity", "create", OWNER, Map.of());
        verify(stravaService, timeout(2_000)).importForAthlete(athleteId);

        service.accept("activity", "create", OWNER, Map.of());
        verify(stravaService, timeout(2_000).times(2)).importForAthlete(athleteId);
    }

    /**
     * Le jeton de validation prouve que l'URL de rappel est bien la nôtre. Non configuré, il ne
     * vaut jamais accord : sinon une instance déployée sans réglage laisserait n'importe qui
     * faire valider cette adresse et détourner le flux d'événements.
     */
    @Test
    void leJetonDeValidationNeSeDevinePas() {
        assertThat(service.verifies("jeton-convenu")).isTrue();
        assertThat(service.verifies("autre-chose")).isFalse();
        assertThat(service.verifies(null)).isFalse();

        ReflectionTestUtils.setField(service, "verifyToken", "");
        assertThat(service.verifies("")).isFalse();
        assertThat(service.verifies(null)).isFalse();
    }
}
