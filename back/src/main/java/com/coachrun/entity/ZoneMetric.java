package com.coachrun.entity;

import com.coachrun.entity.enums.ZoneAnchor;
import com.coachrun.entity.enums.ZoneModel;
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

/**
 * Jointure zone × métrique : déclare quelles métriques une {@link TrainingZone} porte, <b>et sa
 * règle de calcul</b> (ancre + fourchette de %, ou modèle nommé). Le pré-remplissage par athlète
 * en dérive les valeurs concrètes. Cf. PROPOSITION-ZONES-ET-EDITEUR-V2 §3.2-3.3 (chantier zones v2).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "zone_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uk_zone_metric",
                columnNames = {"zone_id", "metric_type_id"}))
public class ZoneMetric extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private TrainingZone zone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metric_type_id", nullable = false)
    private MetricType metricType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /**
     * Ancre de la borne <b>basse</b> (LT2, VMA, FCMAX…) — {@code null} = pas de règle (valeur
     * seulement manuelle). Sert aussi de repli pour la borne haute.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "anchor", length = 24)
    private ZoneAnchor anchor;

    /**
     * Ancre de la borne <b>haute</b>, quand elle diffère de la borne basse. C'est ce qui permet à
     * une zone d'enjamber la frontière LT1 → LT2 sans laisser de trou : le rapport LT1/LT2 varie
     * d'un athlète à l'autre, donc « 100 % de LT1 » et « 92 % de LT2 » ne tombent pas au même
     * endroit pour tout le monde — le bord commun de deux zones voisines doit être exprimé dans une
     * seule référence. {@code null} ⇒ même ancre que la borne basse (cas de toutes les zones dont
     * les deux bornes partagent la même référence).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "high_anchor", length = 24)
    private ZoneAnchor highAnchor;

    /** Borne basse / haute en % de leur ancre respective (ex. 96–103 % du LT2). */
    @Column(name = "low_pct")
    private Double lowPct;

    @Column(name = "high_pct")
    private Double highPct;

    /** Ancre effective de la borne haute : la sienne, ou celle de la borne basse par défaut. */
    public ZoneAnchor effectiveHighAnchor() {
        return highAnchor != null ? highAnchor : anchor;
    }

    /** Modèle nommé d'où dérive la règle (traçabilité UI) ; CUSTOM par défaut. */
    @Enumerated(EnumType.STRING)
    @Column(name = "model", length = 24)
    private ZoneModel model = ZoneModel.CUSTOM;
}
