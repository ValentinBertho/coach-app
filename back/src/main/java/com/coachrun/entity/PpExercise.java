package com.coachrun.entity;

import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.ExerciseLevel;
import com.coachrun.entity.enums.MuscleGroup;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Exercice de la bibliothèque de préparation physique (cf. DARI Lab). Club-scopé. Groupes
 * musculaires et matériel en tables d'association (portables H2/PG). Progression/régression
 * pointent vers un autre exercice.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "pp_exercises")
public class PpExercise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private ExerciseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 16)
    private ExerciseLevel level = ExerciseLevel.INTERMEDIAIRE;

    @Column(name = "objective")
    private String objective;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pp_exercise_muscle_groups",
            joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "muscle_group", length = 32)
    @Enumerated(EnumType.STRING)
    private Set<MuscleGroup> muscleGroups = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pp_exercise_equipment",
            joinColumns = @JoinColumn(name = "exercise_id"))
    @Column(name = "equipment", length = 32)
    @Enumerated(EnumType.STRING)
    private Set<EquipmentType> equipment = new HashSet<>();

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "technical_notes", length = 4096)
    private String technicalNotes;

    @Column(name = "instructions", length = 4096)
    private String instructions;

    @Column(name = "contraindications", length = 2048)
    private String contraindications;

    /** Variante plus difficile / plus facile (FK vers un autre exercice). */
    @Column(name = "progression_id")
    private UUID progressionId;

    @Column(name = "regression_id")
    private UUID regressionId;

    @Column(name = "is_favorite", nullable = false)
    private boolean favorite = false;

    @Column(name = "is_archived", nullable = false)
    private boolean archived = false;

    @Column(name = "use_count", nullable = false)
    private int useCount = 0;
}
