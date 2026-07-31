package com.coachrun.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Source unique de « maintenant » pour le métier, dans le fuseau de l'application
 * ({@code app.timezone}, Europe/Paris par défaut).
 *
 * <p>Les appels statiques {@code LocalDate.now()} / {@code LocalTime.now()} prennent le fuseau de
 * la JVM : en conteneur celui-ci est UTC, donc décalé d'une à deux heures. Un athlète qui ouvre
 * l'application après minuit voyait la séance de la veille, et les rappels partaient à la mauvaise
 * heure. Passer par ce service rend le fuseau explicite et prépare un fuseau par athlète : seules
 * les surcharges de {@link #today(ZoneId)} auront à changer.</p>
 *
 * <p>Ne concerne que les dates et heures <em>civiles</em>. Le stockage des instants reste en UTC
 * ({@code hibernate.jdbc.time_zone}), ce qui est correct et ne doit pas bouger.</p>
 */
@Service
public class ClockService {

    private final Clock clock;

    @Autowired
    public ClockService(@Value("${app.timezone:Europe/Paris}") String timezone) {
        this(Clock.system(ZoneId.of(timezone)));
    }

    /** Constructeur de test : permet de figer l'instant et le fuseau. */
    public ClockService(Clock clock) {
        this.clock = clock;
    }

    /** Fuseau métier de l'application. */
    public ZoneId zone() {
        return clock.getZone();
    }

    /** Date du jour dans le fuseau de l'application. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Date du jour dans un fuseau donné (fuseau par athlète, à venir). */
    public LocalDate today(ZoneId zone) {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Heure locale courante dans le fuseau de l'application. */
    public LocalTime now() {
        return LocalTime.now(clock);
    }

    /** Date et heure locales courantes dans le fuseau de l'application. */
    public LocalDateTime nowDateTime() {
        return LocalDateTime.now(clock);
    }

    /** Instant courant (indépendant du fuseau). */
    public Instant instant() {
        return clock.instant();
    }
}
