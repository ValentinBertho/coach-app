package com.coachrun;

import com.coachrun.exception.ApiException;
import com.coachrun.security.OAuthStateCodec;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** State OAuth signé : intégrité, expiration, correspondance d'athlète (anti-CSRF). */
class OAuthStateCodecTest {

    private final OAuthStateCodec codec = new OAuthStateCodec("test-secret-key-for-oauth-state-hmac");

    @Test
    void issuedStateIsSelfDescribingAndVerifies() {
        UUID athleteId = UUID.randomUUID();
        String state = codec.issue(athleteId);

        assertThat(state.split("\\.")[0]).isEqualTo(athleteId.toString());
        assertThatCode(() -> codec.verify(state, athleteId)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullBlankAndMalformed() {
        UUID athleteId = UUID.randomUUID();
        assertThatThrownBy(() -> codec.verify(null, athleteId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec.verify("", athleteId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec.verify("abc.def", athleteId)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec.verify(athleteId + ".123.???", athleteId)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsTamperedPayloadOrSignature() {
        UUID athleteId = UUID.randomUUID();
        String state = codec.issue(athleteId);
        String[] parts = state.split("\\.");

        // Athlète substitué avec la signature d'origine
        String swapped = UUID.randomUUID() + "." + parts[1] + "." + parts[2];
        assertThatThrownBy(() -> codec.verify(swapped, athleteId)).isInstanceOf(ApiException.class);

        // Signature altérée
        assertThatThrownBy(() -> codec.verify(state + "x", athleteId)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsStateIssuedForAnotherAthlete() {
        String state = codec.issue(UUID.randomUUID());
        assertThatThrownBy(() -> codec.verify(state, UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsStateSignedWithAnotherSecret() {
        UUID athleteId = UUID.randomUUID();
        String foreign = new OAuthStateCodec("another-secret-entirely-different!").issue(athleteId);
        assertThatThrownBy(() -> codec.verify(foreign, athleteId)).isInstanceOf(ApiException.class);
    }

    // --- PKCE (Garmin) ---

    @Test
    void pkceChallengeFollowsRfc7636() {
        // Vecteur de l'annexe B de la RFC 7636.
        assertThat(OAuthStateCodec.pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
                .isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
    }

    @Test
    void pkceVerifierIsStablePerStateAndValidLength() {
        String state = codec.issue(UUID.randomUUID());
        String verifier = codec.pkceVerifier(state);

        assertThat(verifier).isEqualTo(codec.pkceVerifier(state));
        assertThat(verifier).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(codec.pkceVerifier(codec.issue(UUID.randomUUID()))).isNotEqualTo(verifier);
    }

    @Test
    void pkceVerifierCannotBeComputedWithoutTheSecret() {
        String state = codec.issue(UUID.randomUUID());
        assertThat(new OAuthStateCodec("another-secret-entirely-different!").pkceVerifier(state))
                .isNotEqualTo(codec.pkceVerifier(state));
    }
}
