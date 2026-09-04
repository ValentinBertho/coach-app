package com.coachrun.entity;

import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * La fiche publique d'un coach — sa vitrine dans l'annuaire.
 *
 * <h2>Pourquoi une table séparée plutôt que des colonnes sur {@code users}</h2>
 *
 * <p>Un compte n'a aujourd'hui qu'un rôle, et un coach ne peut donc pas être athlète (décision 2 de
 * l'audit d'ouverture au hub). Cette restriction tombera un jour — un coach est souvent aussi un
 * pratiquant. Le jour venu, une table à part se contente d'accueillir une ligne de plus ; des
 * colonnes posées sur la table des comptes auraient demandé de les déplacer, en production, sur
 * l'objet le plus sensible du produit. Le coût est nul aujourd'hui, l'économie sera réelle.</p>
 *
 * <h2>Ce que cette fiche n'est pas</h2>
 *
 * <p>Elle ne porte <b>aucun droit</b>. Publier une fiche ne donne accès à rien de plus ; ce sont
 * les demandes de coaching, puis les relations, qui ouvrent des portes. Une fiche suspendue ne
 * retire pas non plus au coach l'accès à ses athlètes : elle le retire de l'annuaire.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_profiles")
public class CoachProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User coach;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CoachProfileStatus status = CoachProfileStatus.DRAFT;

    /**
     * Identifiant lisible de la fiche, pour {@code /coachs/{slug}}.
     *
     * <p>Posé une fois et jamais réécrit, même si le coach change de nom : une adresse partagée,
     * mise en favori ou indexée doit continuer de répondre. Un slug qui suit le nom casse tous les
     * liens du jour au lendemain, sans que personne ne s'en aperçoive.</p>
     */
    @Column(name = "slug", nullable = false, unique = true, length = 140)
    private String slug;

    /** L'accroche : une ligne, celle qu'on lit dans la liste avant de cliquer. */
    @Column(name = "headline", length = 140)
    private String headline;

    @Column(name = "bio", length = 4000)
    private String bio;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coach_profile_disciplines",
            joinColumns = @JoinColumn(name = "coach_profile_id"))
    @Column(name = "discipline", length = 16)
    @Enumerated(EnumType.STRING)
    private Set<Discipline> disciplines = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coach_profile_specialties",
            joinColumns = @JoinColumn(name = "coach_profile_id"))
    @Column(name = "specialty", length = 32)
    @Enumerated(EnumType.STRING)
    private Set<CoachSpecialty> specialties = new HashSet<>();

    /** Niveaux d'athlètes acceptés ; vide = tous, ce qui est le cas le plus fréquent. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coach_profile_levels",
            joinColumns = @JoinColumn(name = "coach_profile_id"))
    @Column(name = "athlete_level", length = 16)
    @Enumerated(EnumType.STRING)
    private Set<AthleteLevel> levels = new HashSet<>();

    /** Codes ISO 639-1 sur deux lettres (« fr », « en »). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coach_profile_languages",
            joinColumns = @JoinColumn(name = "coach_profile_id"))
    @Column(name = "language", length = 2)
    private Set<String> languages = new HashSet<>();

    @Column(name = "city", length = 120)
    private String city;

    /** Code pays ISO 3166-1 alpha-2 (« FR »). */
    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Coache à distance : c'est le cas le plus courant, et le défaut. */
    @Column(name = "remote", nullable = false)
    private boolean remote = true;

    /** Coache en présentiel : suppose une ville renseignée pour vouloir dire quelque chose. */
    @Column(name = "in_person", nullable = false)
    private boolean inPerson = false;

    @Column(name = "experience_years")
    private Integer experienceYears;

    /**
     * Nombre d'athlètes que le coach accepte de suivre ; {@code null} = pas de plafond déclaré.
     *
     * <p>Sert au coach à se protéger, pas à la plateforme à le contraindre : c'est lui qui accepte
     * ou refuse chaque demande. La fiche l'affiche pour éviter à un athlète de solliciter quelqu'un
     * qui est visiblement complet.</p>
     */
    @Column(name = "capacity_max")
    private Integer capacityMax;

    /** Passage de {@code DRAFT} à {@code PENDING} : ce que l'administrateur voit arriver. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    /**
     * Ce que l'administrateur a écrit en arbitrant.
     *
     * <p>Transmis au coach sur un refus : sans motif, il redépose la même fiche la semaine
     * suivante, et personne n'y gagne. Sur une validation, il reste interne.</p>
     */
    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    /**
     * Délai médian de réponse aux demandes, en heures ; {@code null} tant qu'il n'y a pas de quoi
     * le calculer.
     *
     * <p>Recalculé, jamais saisi. C'est l'un des signaux factuels qui tiennent lieu d'avis tant
     * qu'il n'y a pas assez de relations terminées pour qu'une note veuille dire quelque chose.</p>
     */
    @Column(name = "median_response_hours")
    private Integer medianResponseHours;
}
