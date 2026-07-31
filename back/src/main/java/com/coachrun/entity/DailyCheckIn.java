package com.coachrun.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Check-in matinal déclaré par l'athlète : sommeil, fatigue, douleur, en trois curseurs.
 *
 * <p>Sert un seul but : que l'état de forme existe <strong>avant</strong> la séance. Jusqu'ici la
 * forme ne se mettait à jour qu'au retour post-séance — un athlète qui ne note pas restait
 * indéfiniment « aucun retour récent », et le coach pilotait à l'aveugle le matin, au moment
 * précis où il décide d'adapter la journée.</p>
 *
 * <p><strong>Invariant</strong> : seules la fatigue et la douleur alimentent la forme. Le sommeil
 * est un contexte affiché au coach, jamais un terme du calcul — comme le RPE, qui n'y entre
 * jamais non plus.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "daily_check_ins",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_check_in_athlete_date",
                columnNames = {"athlete_id", "check_date"}))
public class DailyCheckIn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @Column(name = "check_date", nullable = false)
    private LocalDate checkDate;

    /** Qualité de sommeil déclarée 0–10 (contexte, hors calcul de forme). */
    @Column(name = "sleep")
    private Integer sleep;

    /** Fatigue déclarée 0–10 — terme du calcul de forme. */
    @Column(name = "fatigue")
    private Integer fatigue;

    /** Douleur déclarée 0–10 — terme du calcul de forme et des alertes. */
    @Column(name = "pain")
    private Integer pain;
}
