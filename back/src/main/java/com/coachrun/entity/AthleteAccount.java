package com.coachrun.entity;

import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.Sex;
import jakarta.persistence.Column;
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

import java.time.Instant;
import java.time.LocalDate;

/**
 * L'identité d'un athlète sur la plateforme — la personne, pas le dossier.
 *
 * <h2>Pourquoi cette entité existe, et ce qu'elle n'est pas</h2>
 *
 * <p>{@link Athlete} est, depuis l'origine, la <b>fiche de suivi qu'un coach tient sur quelqu'un</b> :
 * elle vit dans son club ({@code club_id} non nullable), elle naît de son geste, et elle porte les
 * données de santé qu'il collecte. Ce modèle est juste tant qu'un athlète n'existe que parce qu'un
 * coach l'a saisi.</p>
 *
 * <p>Le hub renverse l'ordre : l'athlète arrive <b>en premier</b>, sans coach et sans club. Rendre
 * {@code Athlete.club} nullable pour l'accueillir aurait touché la moitié des requêtes de la
 * plateforme, qui scopent toutes par club. Cette entité prend donc l'autre chemin : elle porte
 * l'identité, l'inscription et ce que l'athlète dit de lui-même ; la fiche reste ce qu'elle était,
 * et se crée à l'acceptation d'une demande de coaching.</p>
 *
 * <h2>Ce qu'elle ne porte pas</h2>
 *
 * <p><b>Aucune donnée de santé.</b> Ni seuil, ni test, ni note médicale : elles restent sur
 * {@link Athlete}, chiffrées au repos et gardées par le consentement. L'objet public de l'athlète
 * n'a pas à devenir le lieu où transitent des données de l'article 9.</p>
 *
 * <h2>La limite assumée</h2>
 *
 * <p>Un athlète suivi par deux coachs de deux espaces différents aura <b>deux fiches</b>, donc deux
 * profils physiologiques. C'est le prix accepté pour un chantier qui tient en quelques lots plutôt
 * qu'en un trimestre. Le chemin de sortie reste ouvert : {@code athletes.athlete_account_id} relie
 * déjà les fiches à leur personne, et la physiologie pourra remonter ici sans reprise.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_accounts")
public class AthleteAccount extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    /**
     * Sert au contrôle d'âge à l'inscription libre, et au coach une fois la relation nouée.
     *
     * <p>Non nullable : c'est la seule donnée qui permette d'appliquer la règle des 16 ans, et
     * une inscription qui l'esquive rendrait la règle décorative.</p>
     */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", length = 16)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "discipline", length = 16)
    private Discipline discipline;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", length = 16)
    private AthleteLevel level;

    @Column(name = "city", length = 120)
    private String city;

    @Column(name = "country", length = 2)
    private String country;

    /**
     * Ce que l'athlète cherche, en toutes lettres.
     *
     * <p>C'est la première chose qu'un coach lit d'une demande — avant le niveau, avant la ville.
     * Un objectif écrit vaut mieux qu'une case cochée : « finir mon premier marathon en avril,
     * je reviens d'une fracture de fatigue » dit ce qu'aucune énumération ne dirait.</p>
     */
    @Column(name = "goal", length = 1000)
    private String goal;

    /**
     * L'athlète cherche un coach. Vrai à l'inscription, faux dès qu'il n'en veut plus.
     *
     * <p>Ne conditionne pas ses demandes — il reste maître de qui il sollicite. Il ouvre la porte
     * dans l'autre sens : un coach peut démarcher, et personne ne doit être démarché après avoir
     * dit qu'il ne cherchait plus.</p>
     */
    @Column(name = "looking_for_coach", nullable = false)
    private boolean lookingForCoach = true;

    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;

    /**
     * Consentement au traitement des données de santé, donné à l'inscription.
     *
     * <p>Recopié sur la fiche à l'acceptation d'une demande : sans lui, {@code HealthDataConsentValidator}
     * bloquerait la première mesure, et le coach découvrirait la règle en butant dessus.</p>
     */
    @Column(name = "health_data_consent_at")
    private Instant healthDataConsentAt;
}
