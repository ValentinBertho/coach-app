package com.coachrun.entity;

import com.coachrun.entity.enums.RunDistance;
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

/**
 * Performance de référence d'un athlète (chrono sur une distance), source du calcul VDOT
 * (cf. DARI Lab). Le recalcul du VDOT et des allures est déclenché à chaque ajout/suppression.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_performances")
public class AthletePerformance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(name = "distance", nullable = false, length = 32)
    private RunDistance distance;

    @Column(name = "time_seconds", nullable = false)
    private int timeSeconds;

    @Column(name = "date_set")
    private LocalDate dateSet;
}
