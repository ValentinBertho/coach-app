package com.coachrun.entity;

import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.WorkoutType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Modèle de séance réutilisable (bibliothèque du club). Les étapes structurées sont
 * stockées sérialisées en JSON (peu volumineux) pour éviter une table dédiée.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "workout_templates")
public class WorkoutTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private WorkoutType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "notes", length = 2048)
    private String notes;

    @Column(name = "target_distance_m")
    private Integer targetDistanceM;

    @Column(name = "target_duration_s")
    private Integer targetDurationS;

    /**
     * Effort perçu attendu pour la séance <b>entière</b> (1–10), annoncé par le coach.
     *
     * <p>Distinct du RPE de bloc porté par la structure : un 10 × 400 a des blocs à 9 et un
     * échauffement à 3, sans que « la séance » ait un chiffre. Et distinct du RPE de
     * {@code Workout}, qui est le <b>ressenti</b> de l'athlète — l'un est une consigne, l'autre
     * une mesure, et c'est leur écart qui informe.</p>
     */
    @Column(name = "target_rpe")
    private Integer targetRpe;

    /** JSON sérialisé de la liste d'étapes (WorkoutStepRequest[]). */
    @Column(name = "steps_json", length = 8000)
    private String stepsJson;

    // --- Bibliothèque DARI Lab (prescription en fourchettes structurée) -------

    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", length = 16)
    private Discipline discipline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private SessionCategory category;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    @Column(name = "use_count", nullable = false)
    private int useCount = 0;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /** JSON sérialisé de la structure DARI Lab (échauffement/corps/retour, blocs en fourchettes). */
    @Column(name = "structure_json", columnDefinition = "text")
    private String structureJson;
}
