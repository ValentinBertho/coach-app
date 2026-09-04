package com.coachrun;

import com.coachrun.dto.request.RegisterRequest;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Inscription sur invitation (point 9 de l'audit de bêta ouverte).
 *
 * <p>Le runbook prévoit une cohorte fermée de 5 à 8 coachs, mais {@code /auth/register} était
 * public et n'exigeait que l'unicité de l'e-mail : n'importe qui pouvait créer un club sur
 * l'instance de production, avec les données de santé que cela implique.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RegistrationModeTest {

    @Autowired private AuthService authService;

    private String initialMode;
    private String initialCode;

    /**
     * {@code AuthService} est un singleton du contexte Spring, et ce contexte est <b>mis en
     * cache et partagé</b> par toutes les classes annotées {@code @SpringBootTest
     * @ActiveProfiles("test")}. Forcer le mode d'inscription ici sans le remettre en place le
     * laissait à « invite » pour toute la suite : chaque {@code POST /auth/register} des classes
     * exécutées ensuite repartait en 403, puis en {@code NullPointerException} sur
     * {@code accessToken} absent de la réponse. Le rollback de {@code @Transactional} ne restaure
     * que la base, pas les champs d'un bean.
     *
     * <p>L'ordre des classes de test dépend du système de fichiers : le défaut ne se voyait pas
     * forcément en local et sortait en CI.</p>
     */
    @BeforeEach
    void captureRegistrationSettings() {
        initialMode = (String) ReflectionTestUtils.getField(authService, "registrationMode");
        initialCode = (String) ReflectionTestUtils.getField(authService, "registrationInviteCode");
    }

    @AfterEach
    void restoreRegistrationSettings() {
        mode(initialMode, initialCode);
    }

    private void mode(String mode, String code) {
        ReflectionTestUtils.setField(authService, "registrationMode", mode);
        ReflectionTestUtils.setField(authService, "registrationInviteCode", code);
    }

    private RegisterRequest request(String email, String invitationCode) {
        return new RegisterRequest(email, "password123", "Coach Test", "Club Test",
                false, true, invitationCode);
    }

    /** Inscription d'un coach indépendant : ni club ni nom d'activité. */
    private RegisterRequest soloRequest(String email, String workspaceName) {
        return new RegisterRequest(email, "password123", "Marie Dupont", workspaceName,
                true, true, null);
    }

    @Test
    void openModeAcceptsRegistrationWithoutCode() {
        mode("open", "");

        assertThatCode(() -> authService.register(request("libre@darilab.app", null)))
                .doesNotThrowAnyException();
    }

    @Test
    void inviteModeRejectsMissingCode() {
        mode("invite", "BETA-2026");

        assertThatThrownBy(() -> authService.register(request("sans.code@darilab.app", null)))
                .isInstanceOf(ForbiddenException.class)
                // Message explicite : « accès refusé » laisserait croire à un compte bloqué.
                .hasMessageContaining("Code d'invitation invalide");
    }

    @Test
    void inviteModeRejectsWrongCode() {
        mode("invite", "BETA-2026");

        assertThatThrownBy(() -> authService.register(request("faux.code@darilab.app", "AUTRE")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void inviteModeAcceptsTheRightCode() {
        mode("invite", "BETA-2026");

        var response = authService.register(request("invite@darilab.app", " BETA-2026 "));

        assertThat(response.accessToken()).isNotEmpty();
    }

    /**
     * Un coach indépendant s'inscrit sans nommer quoi que ce soit, et son espace prend son nom.
     *
     * <p>Le champ était {@code @NotBlank} : il fallait inventer un club, ou y mettre son propre
     * nom — et ce nom le suivait ensuite partout. La moitié de la cible annoncée butait donc sur
     * le premier champ du premier écran.</p>
     */
    @Test
    void independentCoachRegistersWithoutNamingAnything() {
        mode("open", "");

        var response = authService.register(soloRequest("solo@darilab.app", null));

        assertThat(response.accessToken()).isNotEmpty();
        assertThat(response.user().clubName()).isEqualTo("Marie Dupont");
        assertThat(response.user().soloPractice()).isTrue();
    }

    /** Un indépendant qui exerce sous un nom d'activité garde le sien : le champ reste offert. */
    @Test
    void independentCoachMayStillNameTheirPractice() {
        mode("open", "");

        var response = authService.register(soloRequest("atelier@darilab.app", "Atelier Foulée"));

        assertThat(response.user().clubName()).isEqualTo("Atelier Foulée");
        assertThat(response.user().soloPractice()).isTrue();
    }

    /**
     * Le nom reste exigé pour un club. Desserrer la validation du DTO ne devait pas ouvrir la
     * porte à des clubs anonymes : la règle dépend d'un autre champ, elle vit donc dans le service.
     */
    @Test
    void clubRegistrationStillRequiresAName() {
        mode("open", "");

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("sans.nom@darilab.app", "password123", "Jean Dupont", null,
                        false, true, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nom du club est requis");
    }

    /** Un coach de club garde son vocabulaire : le drapeau ne s'active pas tout seul. */
    @Test
    void clubRegistrationIsNotSoloPractice() {
        mode("open", "");

        var response = authService.register(request("club@darilab.app", null));

        assertThat(response.user().clubName()).isEqualTo("Club Test");
        assertThat(response.user().soloPractice()).isFalse();
    }

    /** Mode fermé sans code configuré : erreur d'exploitation, message distinct. */
    @Test
    void inviteModeWithoutConfiguredCodeRefusesEveryone() {
        mode("invite", "");

        assertThatThrownBy(() -> authService.register(request("bloque@darilab.app", "peu importe")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("momentanément fermées");
    }
}
