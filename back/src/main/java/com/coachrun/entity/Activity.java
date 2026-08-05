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

    /** FC maximale relevée (bpm) — présente dans la réponse Strava, absente d'un GPX sans capteur. */
    @Column(name = "max_hr")
    private Integer maxHr;

    /** Cadence moyenne en pas par minute (Strava la renvoie par jambe : doublée à l'import). */
    @Column(name = "avg_cadence")
    private Integer avgCadence;

    /** Puissance moyenne (W), pour les athlètes équipés d'un capteur. */
    @Column(name = "avg_power_w")
    private Integer avgPowerW;

    /** Dépense énergétique estimée (kcal). */
    @Column(name = "calories")
    private Integer calories;

    /**
     * Ressenti de l'athlète sur cette sortie (1–10). Distinct du RPE d'une séance prescrite :
     * il porte le vécu d'une sortie qui n'était au programme de personne.
     */
    @Column(name = "rpe")
    private Integer rpe;

    /** Mot de l'athlète à son coach sur cette sortie. */
    @Column(name = "athlete_comment", length = 2000)
    private String athleteComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ActivityStatus status = ActivityStatus.IMPORTED;

    @Column(name = "matched_workout_id")
    private UUID matchedWorkoutId;

    /**
     * Date à laquelle l'athlète a été invité à confirmer le ressenti de la séance rapprochée.
     * Non nulle = déjà proposé : la feuille pré-remplie ne s'ouvre qu'une fois, sinon un refus
     * se transforme en pop-up à chaque lancement.
     */
    @Column(name = "feedback_prompted_at")
    private java.time.Instant feedbackPromptedAt;

    /** Tracé GPS sous-échantillonné, JSON [[lat,lon],…] (GPX/TCX ou polyline Strava décodée). */
    @Column(name = "route_json")
    private String routeJson;

    /** Flux échantillonné JSON [[elapsedS,hr,paceSecPerKm],…] (-1 = absent) pour le temps-en-zone. */
    @Column(name = "stream_json")
    private String streamJson;

    /**
     * Tours de l'activité, JSON {@code {"kind":"DEVICE|SPLIT","laps":[…]}}. {@code DEVICE} = les
     * tours pris par la montre (les répétitions d'un fractionné, ce qu'on veut vraiment lire) ;
     * {@code SPLIT} = des splits kilométriques calculés faute de mieux. Null sur une saisie
     * manuelle, et sur tout ce qui a été importé avant l'arrivée des tours.
     */
    @Column(name = "laps_json")
    private String lapsJson;
}
