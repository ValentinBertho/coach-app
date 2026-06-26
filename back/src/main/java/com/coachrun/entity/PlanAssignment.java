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

import java.time.LocalDate;

/**
 * Attribution d'un plan à un athlète avec sa <strong>date de départ</strong>. Source de vérité du
 * suivi : permet de calculer la semaine courante et l'avancement, là où la simple relation N-N
 * plan↔athlète ne portait aucune temporalité.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "plan_assignments")
public class PlanAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private TrainingPlan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
}
