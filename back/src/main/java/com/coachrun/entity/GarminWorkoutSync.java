package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Une séance Darilab déposée dans le calendrier Garmin d'un athlète (Training API).
 *
 * <p>Identifiants nus plutôt que relations : {@code workoutId} passe à {@code null} quand la séance
 * est supprimée (contrainte {@code ON DELETE SET NULL}), et c'est précisément ce cas que la ligne
 * sert à traiter — retirer de la montre ce qui n'existe plus ici.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "garmin_workout_syncs")
public class GarminWorkoutSync extends BaseEntity {

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    /** Séance d'origine ; null si elle a été supprimée depuis l'envoi. */
    @Column(name = "workout_id")
    private UUID workoutId;

    /** Date à laquelle l'entraînement est planifié chez Garmin. */
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /** Entraînement créé chez Garmin ; null si l'envoi a échoué (il sera retenté). */
    @Column(name = "garmin_workout_id")
    private Long garminWorkoutId;

    @Column(name = "garmin_schedule_id")
    private Long garminScheduleId;

    /** Empreinte du contenu envoyé (entraînement + date) : rien n'est renvoyé tant qu'elle ne change pas. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** Dernière erreur d'envoi, lisible ; null quand tout s'est bien passé. */
    @Column(name = "last_error", length = 500)
    private String lastError;
}
