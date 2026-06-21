package com.coachrun.entity;

import com.coachrun.entity.enums.IntensityZone;
import com.coachrun.entity.enums.WorkoutStepType;
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

/**
 * Étape structurée d'une séance (échauffement, répétitions ×N, récup, retour au calme)
 * avec cible par zone et durée/distance.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "workout_steps")
public class WorkoutStep extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false)
    private Workout workout;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 16)
    private WorkoutStepType stepType;

    /** Nombre de répétitions (≥1 ; 1 pour un bloc simple). */
    @Column(name = "repetitions", nullable = false)
    private int repetitions = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", length = 4)
    private IntensityZone zone;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "duration_s")
    private Integer durationS;

    @Column(name = "notes", length = 512)
    private String notes;
}
