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

    /** Feedback athlète (ressenti d'effort 1–10 + commentaire). */
    @Column(name = "rpe")
    private Integer rpe;

    /** Fatigue et douleur (1–10 / 0–10) — base de l'état de forme (jamais le RPE). */
    @Column(name = "fatigue")
    private Integer fatigue;

    @Column(name = "pain")
    private Integer pain;

    @Column(name = "athlete_comment", length = 1024)
    private String athleteComment;

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

    /** Copie figée de la prescription (SessionStructure JSON) au moment de l'assignation. */
    @Column(name = "session_snapshot", length = 20000)
    private String sessionSnapshot;

    /** Allures/cibles calculées pour cet athlète au moment de l'assignation (JSON). */
    @Column(name = "calculated_paces", length = 20000)
    private String calculatedPaces;

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
