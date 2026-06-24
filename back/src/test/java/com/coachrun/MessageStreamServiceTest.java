package com.coachrun;

import com.coachrun.dto.response.MessageResponse;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.service.MessageStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Diffusion SSE des messages : abonnement par fil, diffusion sûre (sans abonné = no-op). */
class MessageStreamServiceTest {

    private final MessageStreamService service = new MessageStreamService();

    private MessageResponse msg(UserRole role) {
        return new MessageResponse(UUID.randomUUID(), "Bien joué !", role, "Coach Démo",
                UUID.randomUUID(), null, null, null, null, Instant.now());
    }

    @Test
    void subscribeReturnsEmitterWithLongTimeout() {
        SseEmitter emitter = service.subscribe(UUID.randomUUID());
        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(30 * 60 * 1000L);
    }

    @Test
    void broadcastToSubscribedThreadDoesNotThrow() {
        UUID athleteId = UUID.randomUUID();
        service.subscribe(athleteId);
        assertThatCode(() -> service.broadcast(athleteId, msg(UserRole.COACH)))
                .doesNotThrowAnyException();
    }

    @Test
    void broadcastWithoutSubscribersIsNoop() {
        assertThatCode(() -> service.broadcast(UUID.randomUUID(), msg(UserRole.ATHLETE)))
                .doesNotThrowAnyException();
    }
}
