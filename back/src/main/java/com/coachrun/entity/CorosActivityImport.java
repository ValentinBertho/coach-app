package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une sortie listée dans le compte COROS d'un athlète, et ce que l'import en a fait.
 *
 * <p>Sert à ne jamais retélécharger un FIT (COROS en autorise 50 par 24 h) et à tenir ce quota.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coros_activity_imports")
public class CorosActivityImport extends BaseEntity {

    public enum Outcome {
        /** Créée dans Darilab depuis son FIT. */
        IMPORTED,
        /** La même sortie était déjà là (Strava, Garmin, fichier) : rien de créé. */
        MERGED,
        /** Déjà importée auparavant, ou écartée par l'athlète. */
        SKIPPED,
        /** Échec (téléchargement, fichier illisible) : retentée quelques fois. */
        FAILED
    }

    @Column(name = "athlete_id", nullable = false)
    private UUID athleteId;

    /** Identifiant COROS de la sortie ({@code labelId}). */
    @Column(name = "label_id", nullable = false, length = 64)
    private String labelId;

    /** Code sport COROS de la sortie, exigé pour télécharger son FIT. */
    @Column(name = "sport_type")
    private Integer sportType;

    @Column(name = "activity_date")
    private LocalDate activityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private Outcome outcome;

    /** Sortie Darilab créée ou complétée ; null si supprimée depuis ou si rien n'a été créé. */
    @Column(name = "activity_id")
    private UUID activityId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** Quand son FIT a été téléchargé : compte dans le quota COROS de 50 par 24 h. */
    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;
}
