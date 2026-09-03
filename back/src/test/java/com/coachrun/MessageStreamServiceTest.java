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

    @Test
    void heartbeatWithoutSubscribersIsNoop() {
        assertThatCode(service::heartbeat).doesNotThrowAnyException();
        assertThat(service.openStreams()).isZero();
    }

    /**
     * Le battement occupe la connexion pour que le relais ne la coupe pas au bout de 150 s ; il
     * ne doit surtout pas fermer les flux vivants au passage.
     */
    @Test
    void heartbeatKeepsLiveStreamsRegistered() {
        UUID conversationId = UUID.randomUUID();
        service.subscribe(conversationId);
        service.subscribe(conversationId);

        service.heartbeat();

        assertThat(service.openStreams()).isEqualTo(2);
    }

    /** Un flux fermé n'est pas retenu une demi-heure de plus : le battement le révèle. */
    @Test
    void heartbeatDropsClosedStreams() {
        UUID conversationId = UUID.randomUUID();
        SseEmitter emitter = service.subscribe(conversationId);
        emitter.complete();

        service.heartbeat();

        assertThat(service.openStreams()).isZero();
    }

    /** Au-delà du plafond par fil, le plus ancien cède la place — la table ne croît pas. */
    @Test
    void subscribeCapsStreamsPerThread() {
        UUID conversationId = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            service.subscribe(conversationId);
        }
        assertThat(service.openStreams()).isEqualTo(12);
    }
}
