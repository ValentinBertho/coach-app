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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Séance de force planifiée au calendrier d'un athlète (cf. DARI Lab). Miroir de {@link Workout}
 * pour le module force : snapshot figé de la prescription + charges calculées au moment de
 * l'assignation, champs demandés à l'athlète (adaptatifs), et retour de séance global.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "scheduled_strength_sessions")
public class ScheduledStrengthSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    /** Séance de bibliothèque source. */
    @Column(name = "source_session_id")
    private UUID sourceSessionId;

    /** Plan d'entraînement dont cette séance de force est issue (si générée par un plan). */
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "title", nullable = false)
    private String title;

    /** Copie figée de la structure (blocs + exercices prescrits) au moment de l'assignation. */
    @Column(name = "session_snapshot", length = 20000)
    private String sessionSnapshot;

    /** Charges calculées pour cet athlète au moment de l'assignation (JSON). */
    @Column(name = "calculated_charges", length = 20000)
    private String calculatedCharges;

    /** Champs demandés à l'athlète (adaptatif selon le niveau), JSON. */
    @Column(name = "required_fields", length = 1024)
    private String requiredFields;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "original_date")
    private LocalDate originalDate;

    @Column(name = "moved_by_athlete", nullable = false)
    private boolean movedByAthlete = false;

    // --- Retour de séance global ---
    @Column(name = "completed", nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "session_rpe", precision = 3, scale = 1)
    private java.math.BigDecimal sessionRpe;

    @Column(name = "session_fatigue")
    private Integer sessionFatigue;

    @Column(name = "session_pain")
    private Integer sessionPain;

    @Column(name = "session_comment", length = 1024)
    private String sessionComment;
}
