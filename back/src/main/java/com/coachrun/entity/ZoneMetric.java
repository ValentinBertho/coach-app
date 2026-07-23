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

    /** Ancre de calcul (LT2, VMA, FCMAX…) — {@code null} = pas de règle (valeur seulement manuelle). */
    @Enumerated(EnumType.STRING)
    @Column(name = "anchor", length = 24)
    private ZoneAnchor anchor;

    /** Borne basse / haute en % de l'ancre (ex. 96–103 % du LT2). */
    @Column(name = "low_pct")
    private Double lowPct;

    @Column(name = "high_pct")
    private Double highPct;

    /** Modèle nommé d'où dérive la règle (traçabilité UI) ; CUSTOM par défaut. */
    @Enumerated(EnumType.STRING)
    @Column(name = "model", length = 24)
    private ZoneModel model = ZoneModel.CUSTOM;
}
