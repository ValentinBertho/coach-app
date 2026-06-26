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
 * Modèle de mésocycle réutilisable (« méso type ») : la <strong>forme</strong> d'un bloc de
 * périodisation — nombre de semaines, progression hebdomadaire, fréquence et niveau de décharge.
 * Indépendant du contenu des séances : appliqué à partir d'une semaine source (athlète ou groupe),
 * il projette la charge selon ces paramètres.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "mesocycle_templates")
public class MesocycleTemplate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 2048)
    private String description;

    /** Nombre de semaines du bloc (décharge comprise). */
    @Column(name = "weeks", nullable = false)
    private int weeks;

    /** Progression de charge par semaine d'accumulation (%). */
    @Column(name = "increase_pct", nullable = false)
    private double increasePct;

    /** Décharge toutes les N semaines (taille du bloc). */
    @Column(name = "deload_every", nullable = false)
    private int deloadEvery;

    /** Charge de la semaine de décharge (% de la semaine source). */
    @Column(name = "deload_pct", nullable = false)
    private double deloadPct;
}
