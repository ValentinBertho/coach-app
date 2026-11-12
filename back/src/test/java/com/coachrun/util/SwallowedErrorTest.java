package com.coachrun.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** Une erreur avalée laisse une trace, une seule par fenêtre pour une même cause. */
class SwallowedErrorTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(SwallowedErrorTest.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        SwallowedError.reset();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        SwallowedError.reset();
    }

    @Test
    void writesAnErrorLineWithTheCauseAndTheContext() {
        boolean traced = SwallowedError.report(SwallowedErrorTest.class,
                "prescription de la séance 42 illisible", new IllegalStateException("JSON tronqué"));

        assertThat(traced).isTrue();
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("prescription de la séance 42 illisible");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("JSON tronqué");
    }

    @Test
    void doesNotRepeatTheSameErrorWithinTheWindow() {
        for (int i = 0; i < 50; i++) {
            SwallowedError.report(SwallowedErrorTest.class, "flux illisible pour la sortie 7",
                    new IllegalStateException("x"));
        }
        assertThat(appender.list).hasSize(1);
    }

    @Test
    void tracesADifferentContextSeparately() {
        SwallowedError.report(SwallowedErrorTest.class, "sortie 7", new IllegalStateException("x"));
        SwallowedError.report(SwallowedErrorTest.class, "sortie 8", new IllegalStateException("x"));
        assertThat(appender.list).hasSize(2);
    }
}
