package com.coachrun.util;

import com.coachrun.util.ClientProfile.Platform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lire un {@code User-Agent} sans se faire avoir.
 *
 * <p>L'exercice est truffé de déclarations mensongères, et toutes sont volontaires côté éditeurs :
 * un iPad se dit « Macintosh » depuis iPadOS 13, Edge se dit « Chrome », Chrome se dit « Safari »,
 * et sur iOS tous les navigateurs embarquent le moteur de Safari. Un classement écrit dans le
 * mauvais ordre répond donc faux sans jamais échouer — ces tests fixent l'ordre.</p>
 */
class ClientProfileTest {

    @Test
    void aniPhoneIsMobile() {
        ClientProfile p = ClientProfile.of(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
        assertThat(p.platform()).isEqualTo(Platform.MOBILE);
        assertThat(p.os()).isEqualTo("iOS");
        assertThat(p.browser()).isEqualTo("Safari");
    }

    /** Chrome sur iOS se déclare « CriOS » : sans ça, il passerait pour Safari. */
    @Test
    void chromeOniOSIsStillChrome() {
        ClientProfile p = ClientProfile.of(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "CriOS/120.0.0.0 Mobile/15E148 Safari/604.1");
        assertThat(p.platform()).isEqualTo(Platform.MOBILE);
        assertThat(p.browser()).isEqualTo("Chrome");
    }

    @Test
    void anAndroidPhoneIsMobileAndATabletIsNot() {
        ClientProfile phone = ClientProfile.of(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Mobile Safari/537.36");
        assertThat(phone.platform()).isEqualTo(Platform.MOBILE);
        assertThat(phone.os()).isEqualTo("Android");
        assertThat(phone.browser()).isEqualTo("Chrome");

        // Une tablette Android se distingue d'un téléphone par l'ABSENCE de « Mobile ».
        ClientProfile tablet = ClientProfile.of(
                "Mozilla/5.0 (Linux; Android 14; SM-X200) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36");
        assertThat(tablet.platform()).isEqualTo(Platform.TABLET);
    }

    @Test
    void aniPadIsATablet() {
        ClientProfile p = ClientProfile.of(
                "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
        assertThat(p.platform())
                .as("« ipad » prime sur « mobile », que la même chaîne contient aussi")
                .isEqualTo(Platform.TABLET);
        assertThat(p.os()).isEqualTo("iOS");
    }

    @Test
    void aDesktopIsADesktop() {
        assertThat(ClientProfile.of(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36").platform())
                .isEqualTo(Platform.DESKTOP);
        assertThat(ClientProfile.of(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                        + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15").os())
                .isEqualTo("macOS");
    }

    /** Edge se déclare « Chrome » et « Safari » en plus de lui-même : le plus spécifique gagne. */
    @Test
    void edgeIsNotMistakenForChrome() {
        assertThat(ClientProfile.of(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0").browser())
                .isEqualTo("Edge");
    }

    /**
     * « On ne sait pas » est une réponse ; « Ordinateur » par défaut n'en est pas une. Un agent
     * absent ou méconnaissable ne doit pas être rangé au hasard dans une fiche qu'on lit pour
     * décider quoi répondre à quelqu'un.
     */
    @Test
    void anUnknownAgentIsNotGuessed() {
        assertThat(ClientProfile.of(null).platform()).isEqualTo(Platform.UNKNOWN);
        assertThat(ClientProfile.of("").platform()).isEqualTo(Platform.UNKNOWN);
        assertThat(ClientProfile.of("curl/8.4.0").platform()).isEqualTo(Platform.UNKNOWN);
        assertThat(ClientProfile.of("curl/8.4.0").os()).isNull();
    }
}
