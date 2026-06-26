package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Notification in-app destinée à un utilisateur (coach ou athlète) : centre de notifications
 * persistant, indépendant des canaux email/push. {@code readAt == null} ⇒ non lue.
 * Jamais de donnée de santé dans {@code body} (cf. invariant notifications).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Code stable de la catégorie (WORKOUT_PLANNED, ATHLETE_FEEDBACK, COACH_ALERTS…). */
    @Column(nullable = false, length = 48)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 500)
    private String body;

    /** Lien applicatif (route front) à ouvrir au clic. */
    @Column(length = 255)
    private String link;

    @Column(name = "read_at")
    private Instant readAt;
}
