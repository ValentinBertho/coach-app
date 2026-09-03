package com.coachrun;

import com.coachrun.service.NotificationStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Flux SSE du compteur de non-lues : abonnement par utilisateur, plafond par utilisateur, et
 * battement de cœur — celui qui empêche le relais de couper une connexion silencieuse.
 */
class NotificationStreamServiceTest {

    private final NotificationStreamService service = new NotificationStreamService();

    @Test
    void subscribeReturnsEmitterWithLongTimeout() {
        SseEmitter emitter = service.subscribe(UUID.randomUUID());
        assertThat(emitter).isNotNull();
        assertThat(emitter.getTimeout()).isEqualTo(30 * 60 * 1000L);
    }

    @Test
    void publishWithoutSubscribersIsNoop() {
        assertThatCode(() -> service.publishUnread(UUID.randomUUID(), 3))
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
        UUID userId = UUID.randomUUID();
        service.subscribe(userId);
        service.subscribe(userId);

        service.heartbeat();

        assertThat(service.openStreams()).isEqualTo(2);
    }

    /** Un flux fermé n'est pas retenu une demi-heure de plus : le battement le révèle. */
    @Test
    void heartbeatDropsClosedStreams() {
        UUID userId = UUID.randomUUID();
        SseEmitter emitter = service.subscribe(userId);
        emitter.complete();

        service.heartbeat();

        assertThat(service.openStreams()).isZero();
    }

    /** Au-delà du plafond par utilisateur, le plus ancien cède la place. */
    @Test
    void subscribeCapsStreamsPerUser() {
        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 20; i++) {
            service.subscribe(userId);
        }
        assertThat(service.openStreams()).isEqualTo(6);
    }
}
