package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Le mot d'un athlète sur son coach, affiché sur la page publique quand le coach le publie.
 *
 * <p>Un texte, pas une note : des étoiles feraient de l'annuaire un classement, et d'une
 * relation d'entraînement une transaction notée.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "coach_testimonials")
public class CoachTestimonial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_account_id", nullable = false)
    private AthleteAccount athleteAccount;

    /** Prénom et initiale : assez pour que ce soit quelqu'un, pas assez pour le retrouver. */
    @Column(name = "author_name", nullable = false, length = 80)
    private String authorName;

    @Column(name = "body", nullable = false, length = 600)
    private String body;

    @Column(name = "published", nullable = false)
    private boolean published = false;
}
