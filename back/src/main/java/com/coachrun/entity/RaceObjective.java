package com.coachrun.entity;

import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.entity.enums.RacePriority;
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

import java.time.LocalDate;

/** Course cible d'un athlète : date, distance, chrono visé, priorité. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "race_objectives")
public class RaceObjective extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "race_date", nullable = false)
    private LocalDate raceDate;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "target_time_s")
    private Integer targetTimeS;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 1)
    private RacePriority priority = RacePriority.B;

    /**
     * Chrono réellement réalisé, une fois la course courue.
     *
     * <p>Il manquait, et c'était le trou le plus coûteux du modèle : le lendemain d'une course,
     * l'objectif basculait « passé » et ce qui s'était produit n'était enregistré nulle part. Une
     * course est pourtant la meilleure mesure de niveau qu'un athlète produise dans l'année.</p>
     *
     * <p>Ce champ ne vit pas seul : quand la distance correspond à une distance de référence, le
     * chrono est aussi enregistré comme performance, ce qui recalcule le VDOT, les allures et les
     * zones (cf. {@code TrajectoryService#recordResult}).</p>
     */
    @Column(name = "result_time_s")
    private Integer resultTimeS;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RaceObjectiveStatus status = RaceObjectiveStatus.UPCOMING;
}
