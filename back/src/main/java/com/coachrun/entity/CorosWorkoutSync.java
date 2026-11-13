package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une séance Darilab déposée dans le calendrier COROS d'un athlète (via COROS MCP).
 *
 * <p>COROS ne permet ni de supprimer ni de déplacer une séance planifiée : on ne peut que la
 * réécrire. Une séance retirée ou déplacée ici est donc réécrite là-bas en « Annulée par ton
 * coach » ({@link #cancelled}), et la ligne est gardée jusqu'à sa date pour ne pas la réécrire à
 * chaque passe — et pour pouvoir la réutiliser si la séance revient à cette date.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coros_workout_syncs")
public class CorosWorkoutSync extends BaseEntity {

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    /** Séance d'origine ; null si elle a été supprimée depuis l'envoi. */
    @Column(name = "workout_id")
    private UUID workoutId;

    /** Date de la séance dans le calendrier COROS (elle ne peut pas y changer). */
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    /**
     * Identifiant COROS de la séance à cette date ({@code idInPlan}), nécessaire pour la
     * réécrire. Null si l'envoi a échoué (il sera retenté) ou si COROS ne l'a pas renvoyé.
     */
    @Column(name = "id_in_plan", length = 64)
    private String idInPlan;

    /** Empreinte du contenu envoyé : rien n'est renvoyé tant qu'elle ne change pas. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    /** La séance a été réécrite en « Annulée par ton coach ». */
    @Column(name = "cancelled", nullable = false)
    private boolean cancelled;

    /** Titre envoyé, repris dans le message d'annulation. */
    @Column(name = "title", length = 120)
    private String title;

    /** Dernière erreur d'envoi, lisible ; null quand tout s'est bien passé. */
    @Column(name = "last_error", length = 500)
    private String lastError;
}
