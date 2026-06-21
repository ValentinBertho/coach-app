package com.coachrun.entity;

import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivityStatus;
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
import java.util.UUID;

/**
 * Activité réalisée (importée). Dédupliquée par (athlete, source, externalId) pour ne pas
 * fausser la charge. Peut être rapprochée d'une séance prévue (matchedWorkoutId).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "activities")
public class Activity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ActivitySource source;

    /** Identifiant externe (Strava/Garmin) pour la déduplication ; null si saisie manuelle. */
    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "title")
    private String title;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "duration_s")
    private Integer durationS;

    @Column(name = "avg_hr")
    private Integer avgHr;

    @Column(name = "elevation_gain_m")
    private Integer elevationGainM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ActivityStatus status = ActivityStatus.IMPORTED;

    @Column(name = "matched_workout_id")
    private UUID matchedWorkoutId;
}
