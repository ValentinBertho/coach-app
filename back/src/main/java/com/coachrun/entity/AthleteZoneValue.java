package com.coachrun.entity;

import com.coachrun.entity.enums.ZoneValueSource;
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
 * Valeur d'une métrique d'une zone pour un athlète donné (façon Nolio : la fiche athlète porte
 * les valeurs). Fourchette {@code valueMin..valueMax} exprimée dans l'unité de la métrique
 * (allure en s/km, FC en bpm…). Cf. AUDIT-COACH-ZONES-METRIQUES §3.1 (chantier Z2).
 *
 * <p>{@code source=AUTO} : générée par le moteur (resync), écrasable ; {@code MANUAL} : imposée
 * par le coach. {@code locked} protège une valeur AUTO contre le prochain resync.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "athlete_zone_values",
        uniqueConstraints = @UniqueConstraint(name = "uk_athlete_zone_metric",
                columnNames = {"athlete_id", "zone_id", "metric_type_id"}))
public class AthleteZoneValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "athlete_id", nullable = false)
    private Athlete athlete;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private TrainingZone zone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "metric_type_id", nullable = false)
    private MetricType metricType;

    @Column(name = "value_min")
    private Double valueMin;

    @Column(name = "value_max")
    private Double valueMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private ZoneValueSource source = ZoneValueSource.AUTO;

    @Column(name = "locked", nullable = false)
    private boolean locked = false;
}
