package com.coachrun;

import com.coachrun.security.RateLimitFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Couverture des routes rate-limitées : les routes porteuses de token partagent un bucket
 * stable (sinon chaque token essayé ouvrirait sa propre fenêtre de comptage).
 */
class RateLimitFilterTest {

    @Test
    void sensitiveRoutesAreBucketed() {
        assertThat(RateLimitFilter.bucket("/api/auth/login")).isEqualTo("auth-login");
        assertThat(RateLimitFilter.bucket("/api/auth/register")).isEqualTo("auth-register");
        assertThat(RateLimitFilter.bucket("/api/auth/refresh")).isEqualTo("auth-refresh");
        assertThat(RateLimitFilter.bucket("/api/public/password-reset")).isEqualTo("password-reset");
        assertThat(RateLimitFilter.bucket("/api/public/verify-email/tok-b")).isEqualTo("verify-email");
        assertThat(RateLimitFilter.bucket("/api/public/invitations/tok/accept")).isEqualTo("invitation-accept");
    }

    @Test
    void tokenRoutesShareOneBucketRegardlessOfToken() {
        assertThat(RateLimitFilter.bucket("/api/public/password-reset/tok-a"))
                .isEqualTo(RateLimitFilter.bucket("/api/public/password-reset/tok-b"));
        assertThat(RateLimitFilter.bucket("/api/public/verify-email/t1"))
                .isEqualTo(RateLimitFilter.bucket("/api/public/verify-email/t2"));
    }

    @Test
    void ordinaryRoutesAreNotLimited() {
        assertThat(RateLimitFilter.bucket("/api/auth/me")).isNull();
        assertThat(RateLimitFilter.bucket("/api/clubs/x/athletes")).isNull();
        assertThat(RateLimitFilter.bucket("/api/public/ping")).isNull();
    }
}
