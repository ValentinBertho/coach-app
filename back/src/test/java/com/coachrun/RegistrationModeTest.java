package com.coachrun;

import com.coachrun.dto.request.RegisterRequest;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.service.AuthService;
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

    private void mode(String mode, String code) {
        ReflectionTestUtils.setField(authService, "registrationMode", mode);
        ReflectionTestUtils.setField(authService, "registrationInviteCode", code);
    }

    private RegisterRequest request(String email, String invitationCode) {
        return new RegisterRequest(email, "password123", "Coach Test", "Club Test", true, invitationCode);
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

    /** Mode fermé sans code configuré : erreur d'exploitation, message distinct. */
    @Test
    void inviteModeWithoutConfiguredCodeRefusesEveryone() {
        mode("invite", "");

        assertThatThrownBy(() -> authService.register(request("bloque@darilab.app", "peu importe")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("momentanément fermées");
    }
}
