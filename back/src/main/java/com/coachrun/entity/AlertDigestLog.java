package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Mémoire du digest quotidien : quand telle alerte a été envoyée à tel coach pour tel athlète.
 *
 * <p><b>Pourquoi.</b> Le digest recalculait les alertes chaque matin et les envoyait toutes, sans
 * jamais se souvenir de ce qu'il avait déjà dit. Un athlète blessé produisait le même paquet —
 * séances manquées, athlète silencieux, charge en baisse — tous les jours, pendant toute la durée
 * de sa blessure. Sur un club de vingt-cinq athlètes, c'est le moment où le coach cesse de lire
 * ses alertes, et où le signal utile se perd avec le reste.</p>
 *
 * <p>Ce n'est pas une mise en sourdine définitive : une alerte persistante est rappelée après un
 * délai, et une alerte qui disparaît puis revient repart de zéro.</p>
 */
@Entity
@Table(name = "alert_digest_log")
@Getter
@Setter
public class AlertDigestLog extends BaseEntity {

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    /** Type d'alerte tel que produit par le tableau de bord : PAIN, ACWR_HIGH, MISSED… */
    @Column(name = "alert_type", nullable = false, length = 32)
    private String alertType;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;
}
