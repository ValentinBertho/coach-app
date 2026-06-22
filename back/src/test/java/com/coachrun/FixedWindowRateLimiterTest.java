package com.coachrun;

import com.coachrun.util.FixedWindowRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    @Test
    void allowsUpToMaxThenBlocks() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofSeconds(60));
        long now = 1_000_000L;
        assertThat(limiter.tryAcquire("ip", now)).isTrue();
        assertThat(limiter.tryAcquire("ip", now)).isTrue();
        assertThat(limiter.tryAcquire("ip", now)).isTrue();
        assertThat(limiter.tryAcquire("ip", now)).isFalse(); // 4e bloquée
    }

    @Test
    void resetsAfterWindow() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofSeconds(60));
        long now = 1_000_000L;
        assertThat(limiter.tryAcquire("ip", now)).isTrue();
        assertThat(limiter.tryAcquire("ip", now)).isFalse();
        assertThat(limiter.tryAcquire("ip", now + 61_000L)).isTrue(); // nouvelle fenêtre
    }

    @Test
    void keysAreIndependent() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofSeconds(60));
        long now = 1_000_000L;
        assertThat(limiter.tryAcquire("a", now)).isTrue();
        assertThat(limiter.tryAcquire("b", now)).isTrue();
    }
}
