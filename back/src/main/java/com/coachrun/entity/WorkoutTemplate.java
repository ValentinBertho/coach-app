package com.coachrun.entity;

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

    /** JSON sérialisé de la liste d'étapes (WorkoutStepRequest[]). */
    @Column(name = "steps_json", length = 8000)
    private String stepsJson;
}
