package com.coachrun.entity;

import com.coachrun.entity.enums.MetricDirection;
import com.coachrun.entity.enums.MetricFormat;
import com.coachrun.entity.enums.MetricUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catalogue extensible de métriques mesurables d'une zone (allure, FC, vitesse, %FCmax,
 * puissance, RPE…). Cf. AUDIT-COACH-ZONES-METRIQUES §3.1.
 *
 * <p>Une métrique {@code is_builtin} avec {@code club == null} appartient au catalogue global
 * (seedé par migration 044) ; une métrique custom est rattachée à un club.</p>
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "metric_types")
public class MetricType extends BaseEntity {

    /** Club propriétaire d'une métrique custom, ou {@code null} pour le catalogue global. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    /** Code stable (PACE, HR, SPEED, PCT_HRMAX, POWER, RPE…). */
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 16)
    private MetricUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private MetricFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private MetricDirection direction;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "is_builtin", nullable = false)
    private boolean builtin = false;
}
