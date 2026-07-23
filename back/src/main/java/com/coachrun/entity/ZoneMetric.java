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

/**
 * Jointure zone × métrique : déclare quelles métriques une {@link TrainingZone} porte
 * (allure, FC, puissance…). Cf. AUDIT-COACH-ZONES-METRIQUES §3.1.
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
}
