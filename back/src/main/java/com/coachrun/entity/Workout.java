package com.coachrun.entity;

import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Séance prescrite (planifiée) pour un athlète, à une date donnée. Composée d'étapes
 * structurées. {@code clubId} dénormalisé pour le scoping tenant et l'index (club, date).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "workouts")
public class Workout extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private WorkoutType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WorkoutStatus status = WorkoutStatus.PLANNED;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "notes", length = 2048)
    private String notes;

    /** Cibles globales optionnelles (résumé) ; le détail est dans les étapes. */
    @Column(name = "target_distance_m")
    private Integer targetDistanceM;

    @Column(name = "target_duration_s")
    private Integer targetDurationS;

    /**
     * Durée <strong>réellement</strong> effectuée (secondes), déclarée par l'athlète sur une séance
     * écourtée. Distincte de {@link #targetDurationS}, qui est une cible.
     *
     * <p>Sans elle, la charge sRPE valait {@code RPE × durée prescrite} : une sortie longue de
     * 1 h 45 abandonnée à 40 minutes pesait 735 UA au lieu de 280, et deux abandons dans la semaine
     * suffisaient à déclencher « charge en forte hausse » sur un athlète qui s'était entraîné
     * moins. Nulle sur l'historique et sur toute séance menée à son terme.</p>
     */
    @Column(name = "actual_duration_s")
    private Integer actualDurationS;

    /** Motif renseigné quand l'athlète déclare la séance non faite ({@code MISSED}). */
    @Enumerated(EnumType.STRING)
    @Column(name = "missed_reason", length = 32)
    private com.coachrun.entity.enums.MissedReason missedReason;

    /**
     * Effort perçu <b>attendu</b> (1–10), figé à l'assignation depuis le modèle.
     *
     * <p>Figé, et non lu à travers le modèle : modifier la bibliothèque des mois plus tard ne
     * doit pas réécrire ce qui avait été annoncé à l'athlète pour une séance déjà courue — le
     * reste de la prescription suit déjà cette règle.</p>
     */
    @Column(name = "target_rpe")
    private Integer targetRpe;

    /** Feedback athlète (ressenti d'effort 1–10 + commentaire). */
    @Column(name = "rpe")
    private Integer rpe;

    /** Fatigue et douleur (1–10 / 0–10) — base de l'état de forme (jamais le RPE). */
    @Column(name = "fatigue")
    private Integer fatigue;

    @Column(name = "pain")
    private Integer pain;

    /**
     * Sensation générale de la séance (1 = excellente … 5 = très mauvaise).
     *
     * <p>Distincte du RPE, qui mesure la <em>difficulté</em> : une séance de seuil peut être très
     * dure et très bien vécue, un footing facile peut être pénible. Le coach lisait jusqu'ici un
     * chiffre d'effort sans jamais savoir dans quel état l'athlète en était sorti.</p>
     */
    @Column(name = "feel")
    private Integer feel;

    /** Blessures déclarées au débrief, JSON {@code [{"kind":…,"area":…,"side":…,"note":…}]}. */
    @Column(name = "injuries_json", length = 2000)
    private String injuriesJson;

    @Column(name = "athlete_comment", length = 1024)
    private String athleteComment;

    /** Retour traité par le coach (file « retours à traiter »). Null = pas encore vu. */
    @Column(name = "coach_reviewed_at")
    private java.time.Instant coachReviewedAt;

    /**
     * Le « vu 👏 » du coach : une reconnaissance <b>adressée à l'athlète</b>, distincte de
     * {@link #coachReviewedAt}.
     *
     * <p>La distinction n'est pas cosmétique. « Traité » est une date que le coach pose pour vider
     * sa propre file, et que l'athlète ne voit jamais ; le « vu » est la seule de ces deux dates
     * qui lui revienne. Les confondre reviendrait à notifier l'athlète chaque fois qu'un coach
     * fait le ménage dans sa file — c'est-à-dire à transformer une attention en bruit.</p>
     */
    @Column(name = "coach_acknowledged_at")
    private java.time.Instant coachAcknowledgedAt;

    /**
     * Retour du coach sur la séance réalisée, visible par l'athlète. Distinct de {@code notes}
     * (consigne posée AVANT la séance) et de {@code athleteComment} (ressenti déclaré).
     */
    @Column(name = "coach_comment", length = 1024)
    private String coachComment;

    @Column(name = "coach_comment_at")
    private java.time.Instant coachCommentAt;

    /**
     * Quand l'athlète a ouvert le commentaire du coach. Nul = non lu.
     *
     * <p>Remis à nul à chaque nouveau commentaire : un coach qui réécrit sur la même séance pose
     * un nouveau message, pas une correction du précédent — et l'athlète doit être averti des
     * deux.</p>
     */
    @Column(name = "coach_comment_read_at")
    private java.time.Instant coachCommentReadAt;

    // --- Calendrier DARI Lab : déplacement athlète + snapshot figé -----------

    /** L'athlète a déplacé la séance (il peut déplacer, jamais modifier le contenu). */
    @Column(name = "moved_by_athlete", nullable = false)
    private boolean movedByAthlete = false;

    /** Date initiale avant le premier déplacement par l'athlète. */
    @Column(name = "original_date")
    private LocalDate originalDate;

    /** Séance de bibliothèque source (si prescrite depuis un modèle). */
    @Column(name = "source_template_id")
    private UUID sourceTemplateId;

    /** Plan d'entraînement dont cette séance est issue (si générée par l'application d'un plan). */
    @Column(name = "plan_id")
    private UUID planId;

    /** Copie figée de la prescription (SessionStructure JSON) au moment de l'assignation. */
    @Column(name = "session_snapshot", columnDefinition = "text")
    private String sessionSnapshot;

    /** Allures/cibles calculées pour cet athlète au moment de l'assignation (JSON). */
    @Column(name = "calculated_paces", columnDefinition = "text")
    private String calculatedPaces;

    /**
     * Charge prévue (UA) : sRPE Foster appliqué à la prescription (RPE de bloc × durée estimée).
     * Distincte de la charge réalisée, qui se calcule depuis le retour de l'athlète.
     */
    @Column(name = "planned_load_ua")
    private Integer plannedLoadUa;

    /** Ordre d'affichage au sein d'un même jour (glisser-déposer intra-jour). */
    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<WorkoutStep> steps = new ArrayList<>();

    public void replaceSteps(List<WorkoutStep> newSteps) {
        this.steps.clear();
        int i = 0;
        for (WorkoutStep step : newSteps) {
            step.setWorkout(this);
            step.setOrderIndex(i++);
            this.steps.add(step);
        }
    }
}
