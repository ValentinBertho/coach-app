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

import java.time.LocalDate;

/**
 * Check-in matinal déclaré par l'athlète, un par jour.
 *
 * <p>L'état de forme DARI Lab est <strong>fatigue + douleur</strong>, jamais le RPE. Il ne se
 * nourrissait que du retour post-séance : sans séance, la pastille de forme du coach vieillissait
 * jusqu'à ne plus rien dire. Ce check-in donne le même signal AVANT l'effort.</p>
 *
 * <p>Le sommeil est un contexte utile au coach (« mal dormi, fatigue 7 » ne se lit pas comme
 * « bien dormi, fatigue 7 »), pas une entrée du calcul de forme : l'inclure changerait la
 * définition de l'invariant.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "wellness_check_ins")
public class WellnessCheckIn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    /** Qualité de sommeil 1–10 (10 = excellente). Contexte, pas facteur de forme. */
    @Column(name = "sleep")
    private Integer sleep;

    /** Fatigue ressentie 0–10 (10 = épuisé) — entre dans l'état de forme. */
    @Column(name = "fatigue")
    private Integer fatigue;

    /** Douleur 0–10 (0 = aucune) — entre dans l'état de forme et les alertes. */
    @Column(name = "pain")
    private Integer pain;
}
