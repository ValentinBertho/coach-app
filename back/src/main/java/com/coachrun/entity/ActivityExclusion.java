package com.coachrun.entity;

import com.coachrun.entity.enums.ActivitySource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Sortie qu'un athlète a écartée pour de bon : la synchro ne doit plus la rapporter.
 *
 * <p>Supprimer ne suffisait pas — l'import suivant la ramenait, et l'athlète effaçait en boucle
 * une sortie qui n'était pas la sienne, un trajet domicile-travail, ou le doublon d'une montre et
 * d'une application enregistrant la même course.</p>
 *
 * <p>C'est une pierre tombale, pas une corbeille : la sortie est bien supprimée, il ne reste que
 * de quoi la reconnaître si elle se représente — et de quoi la nommer à l'écran, car un athlète
 * qui veut revenir sur sa décision a besoin de lire autre chose qu'un identifiant Strava.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "activity_exclusions",
        uniqueConstraints = @UniqueConstraint(name = "uk_activity_exclusion",
                columnNames = {"athlete_id", "source", "external_id"}))
public class ActivityExclusion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    /**
     * La source fait partie de la clé : deux fournisseurs peuvent employer le même identifiant,
     * et écarter une sortie Strava ne dit rien de celle d'une montre Garmin.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ActivitySource source;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    /** Recopiés à la suppression : la sortie n'existe plus, l'écran doit pourtant la nommer. */
    @Column(name = "title")
    private String title;

    @Column(name = "activity_date")
    private LocalDate activityDate;
}
